package com.whitefog.content.block.entity;

import com.whitefog.WhiteFogConfig;
import com.whitefog.content.WhiteFogContent;
import com.whitefog.station.StationDropHelper;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.UUID;

/**
 * Block entity станции «Плоский камень» (этап 1.4).
 *
 * <p>Схема: {@code schemaVersion}, владелец активного job, входной escrow, progress, output,
 * режим {@link Mode} ({@code CRAFT}/{@code FORGE}), {@code revision}. Пустая станция НЕ хранит
 * данные игрока. Поля сериализуются в NBT через MC 26.2 API {@link ValueOutput}/{@link ValueInput}
 * (не legacy {@code CompoundTag}-сигнатуры), поэтому active job переживает save/load.</p>
 *
 * <p><b>Задел:</b> сам job (рецепты инструментов) появится на этапе 3.5; здесь есть только
 * схема, guards занятости и выдача escrow/output. {@code revision} увеличивается при каждом
 * серверном изменении содержимого и используется для guard'ов однократности.</p>
 */
public final class FlatStoneBlockEntity extends BlockEntity {

	/** Режим работы станции (задел под этап 3.5). */
	public enum Mode {
		/** Ручная сборка/обработка. */
		CRAFT,
		/** Кузнечная обработка. */
		FORGE;

		public static Mode byName(String name) {
			for (Mode mode : values()) {
				if (mode.name().equals(name)) {
					return mode;
				}
			}
			return CRAFT;
		}
	}

	private int schemaVersion = WhiteFogConfig.FLAT_STONE_SCHEMA_VERSION;
	private long revision;
	private UUID jobOwner;
	private ItemStack escrow = ItemStack.EMPTY;
	private ItemStack output = ItemStack.EMPTY;
	private int jobProgressTicks;
	private int jobRequiredTicks;
	private Mode mode = Mode.CRAFT;

	public FlatStoneBlockEntity(BlockPos pos, BlockState state) {
		super(WhiteFogContent.FLAT_STONE_BLOCK_ENTITY, pos, state);
	}

	// ------------------------------------------------------------------
	// Состояние
	// ------------------------------------------------------------------

	public int schemaVersion() {
		return this.schemaVersion;
	}

	public long revision() {
		return this.revision;
	}

	public boolean hasActiveJob() {
		return this.jobOwner != null;
	}

	public boolean hasContents() {
		return !this.escrow.isEmpty() || !this.output.isEmpty();
	}

	public boolean isBusy() {
		return hasActiveJob() || hasContents();
	}

	public UUID jobOwner() {
		return this.jobOwner;
	}

	public ItemStack escrow() {
		return this.escrow;
	}

	public ItemStack output() {
		return this.output;
	}

	public int jobProgressTicks() {
		return this.jobProgressTicks;
	}

	public int jobRequiredTicks() {
		return this.jobRequiredTicks;
	}

	public Mode mode() {
		return this.mode;
	}

	public void bumpRevision() {
		this.revision++;
		this.setChanged();
	}

	/** Запускает job (задел этапа 3.5): владелец, режим и требуемое время. */
	public void setActiveJob(UUID owner, Mode mode, int requiredTicks) {
		this.jobOwner = owner;
		this.mode = mode == null ? Mode.CRAFT : mode;
		this.jobRequiredTicks = Math.max(0, requiredTicks);
		this.jobProgressTicks = 0;
		bumpRevision();
	}

	public void setJobProgressTicks(int progress) {
		this.jobProgressTicks = Math.max(0, progress);
		bumpRevision();
	}

	/** Снимает job, сохраняя escrow/output (материалы возвращаются отдельно). */
	public void clearJob() {
		this.jobOwner = null;
		this.jobProgressTicks = 0;
		this.jobRequiredTicks = 0;
		bumpRevision();
	}

	public void setEscrow(ItemStack stack) {
		this.escrow = stack == null ? ItemStack.EMPTY : stack;
		bumpRevision();
	}

	public void setOutput(ItemStack stack) {
		this.output = stack == null ? ItemStack.EMPTY : stack;
		bumpRevision();
	}

	/**
	 * Штатный 26.2-хук удаления block entity (вызывается {@code LevelChunk#removeBlockEntity}
	 * до {@code setRemoved}). Так содержимое выдают контейнеры; здесь — только escrow/output.
	 * Block item не дублируется: его выдаёт loot table ровно один раз.
	 */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState state) {
		super.preRemoveSideEffects(pos, state);
		if (this.level instanceof ServerLevel serverLevel) {
			ejectContents(serverLevel, pos);
		}
	}

	/**
	 * Выдаёт escrow/output на землю (единственный источник содержимого при снятии/потере).
	 * Block item здесь не выдаётся — его даёт loot table.
	 */
	public void ejectContents(ServerLevel level, BlockPos pos) {
		if (!this.escrow.isEmpty()) {
			StationDropHelper.dropAt(level, pos, this.escrow.copy());
			this.escrow = ItemStack.EMPTY;
		}
		if (!this.output.isEmpty()) {
			StationDropHelper.dropAt(level, pos, this.output.copy());
			this.output = ItemStack.EMPTY;
		}
		this.setChanged();
	}

	// ------------------------------------------------------------------
	// NBT (MC 26.2 ValueOutput / ValueInput)
	// ------------------------------------------------------------------

	@Override
	protected void saveAdditional(ValueOutput output) {
		super.saveAdditional(output);
		output.putInt("schema_version", this.schemaVersion);
		output.putLong("revision", this.revision);
		output.putString("job_owner", this.jobOwner == null ? "" : this.jobOwner.toString());
		output.putString("mode", this.mode.name());
		output.putInt("job_progress", this.jobProgressTicks);
		output.putInt("job_required", this.jobRequiredTicks);
		output.store("escrow", ItemStack.OPTIONAL_CODEC, this.escrow);
		output.store("output", ItemStack.OPTIONAL_CODEC, this.output);
	}

	@Override
	protected void loadAdditional(ValueInput input) {
		super.loadAdditional(input);
		this.schemaVersion = input.getIntOr("schema_version", WhiteFogConfig.FLAT_STONE_SCHEMA_VERSION);
		this.revision = input.getLongOr("revision", 0L);
		this.jobOwner = parseUuid(input.getStringOr("job_owner", ""));
		this.mode = Mode.byName(input.getStringOr("mode", Mode.CRAFT.name()));
		this.jobProgressTicks = Math.max(0, input.getIntOr("job_progress", 0));
		this.jobRequiredTicks = Math.max(0, input.getIntOr("job_required", 0));
		this.escrow = input.read("escrow", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
		this.output = input.read("output", ItemStack.OPTIONAL_CODEC).orElse(ItemStack.EMPTY);
	}

	private static UUID parseUuid(String value) {
		if (value == null || value.isEmpty()) {
			return null;
		}
		try {
			return UUID.fromString(value);
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
