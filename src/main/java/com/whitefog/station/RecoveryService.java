package com.whitefog.station;

import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogConfig;
import com.whitefog.content.WhiteFogContent;
import com.whitefog.content.block.FlatStoneBlock;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Резервный recovery-взаимодействие «2 cobblestone → 1 поставленный flat_stone» (этап 1.4).
 *
 * <p>Это <b>world interaction, не ванильный крафт</b>: Shift+ПКМ {@code minecraft:cobblestone}
 * по твёрдой земле при наличии второго cobblestone в инвентаре запускает серверную задачу на
 * {@value com.whitefog.WhiteFogConfig#RECOVERY_FLAT_STONE_TICKS} тиков.</p>
 *
 * <p>Атомарность: входы НЕ списываются на старте — только при успешном завершении после
 * повторной серверной проверки. Поэтому движение/урон/смерть/смена измерения отменяют задачу
 * без потерь (отдельный «возврат» не требуется по построению).</p>
 */
public final class RecoveryService {

	/** Активная recovery-задача игрока. */
	private static final class Job {
		final ResourceKey<Level> dimension;
		final BlockPos target;
		final double startX;
		final double startY;
		final double startZ;
		final float startHealth;
		final long startTick;

		Job(ResourceKey<Level> dimension, BlockPos target, double startX, double startY, double startZ,
				float startHealth, long startTick) {
			this.dimension = dimension;
			this.target = target;
			this.startX = startX;
			this.startY = startY;
			this.startZ = startZ;
			this.startHealth = startHealth;
			this.startTick = startTick;
		}
	}

	private static final Map<UUID, Job> JOBS = new HashMap<>();
	private static boolean registered = false;

	private RecoveryService() {
	}

	/** Идемпотентная регистрация (сервис живёт в общем серверном тике мода). */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		WhiteFog.LOGGER.info(
				"White Fog: flat-stone recovery registered (stage 1.4, 2 cobblestone -> 1 flat_stone, {} ticks)",
				WhiteFogConfig.RECOVERY_FLAT_STONE_TICKS);
	}

	/**
	 * Чистая проверка возможности запуска. Вызывается И на клиенте, И на сервере по
	 * синхронизированному состоянию, чтобы клиентское предсказание совпадало с сервером.
	 */
	public static boolean canStart(Player player, Level level, BlockPos ground) {
		if (player == null) {
			return false;
		}
		BlockPos target = ground.above();
		if (!level.isLoaded(ground) || !level.isLoaded(target)) {
			return false;
		}
		BlockState groundState = level.getBlockState(ground);
		if (!groundState.isFaceSturdy(level, ground, Direction.UP)) {
			return false;
		}
		BlockState targetState = level.getBlockState(target);
		if (!targetState.canBeReplaced() || targetState.liquid()) {
			return false;
		}
		return countItem(player, Items.COBBLESTONE) >= WhiteFogConfig.RECOVERY_COBBLESTONE_COST;
	}

	/** Запускает задачу. Расхода нет — только фиксация владельца/цели/стартовых значений. */
	public static void start(ServerPlayer player, ServerLevel level, BlockPos ground) {
		long now = level.getLevelData().getGameTime();
		JOBS.put(player.getUUID(), new Job(level.dimension(), ground.above(),
				player.getX(), player.getY(), player.getZ(), player.getHealth(), now));
	}

	public static boolean hasJob(ServerPlayer player) {
		return player != null && JOBS.containsKey(player.getUUID());
	}

	/** Серверный тик всех recovery-задач; вызывается из единого END_SERVER_TICK мода. */
	public static void tickAll(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		try {
			for (ServerPlayer player : server.getPlayerList().getPlayers()) {
				try {
					tick(player);
				} catch (RuntimeException e) {
					JOBS.remove(player.getUUID());
					WhiteFog.LOGGER.error("White Fog: recovery tick failed for {}", player.getStringUUID(), e);
				}
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: recovery tick pass failed", e);
		}
	}

	private static void tick(ServerPlayer player) {
		Job job = JOBS.get(player.getUUID());
		if (job == null) {
			return;
		}
		if (!player.isAlive() || !player.level().dimension().equals(job.dimension)
				|| player.getHealth() < job.startHealth) {
			JOBS.remove(player.getUUID());
			return;
		}
		double dx = player.getX() - job.startX;
		double dy = player.getY() - job.startY;
		double dz = player.getZ() - job.startZ;
		if (dx * dx + dy * dy + dz * dz > WhiteFogConfig.RECOVERY_CANCEL_MOVE_SQR) {
			JOBS.remove(player.getUUID());
			return;
		}
		ServerLevel level = player.level();
		long now = level.getLevelData().getGameTime();
		if (now - job.startTick < WhiteFogConfig.RECOVERY_FLAT_STONE_TICKS) {
			return;
		}
		// Завершение: повторная серверная проверка входов (атомарность — списываем только сейчас).
		JOBS.remove(player.getUUID());
		if (countItem(player, Items.COBBLESTONE) < WhiteFogConfig.RECOVERY_COBBLESTONE_COST) {
			return;
		}
		BlockPos target = job.target;
		if (!level.isLoaded(target)) {
			return;
		}
		BlockState targetState = level.getBlockState(target);
		if (!targetState.canBeReplaced() || targetState.liquid()) {
			return;
		}
		consumeItem(player, Items.COBBLESTONE, WhiteFogConfig.RECOVERY_COBBLESTONE_COST);
		BlockState placed = WhiteFogContent.FLAT_STONE.defaultBlockState()
				.setValue(FlatStoneBlock.FACING, player.getDirection().getOpposite());
		level.setBlock(target, placed, Block.UPDATE_ALL);
	}

	/** Очистка при disconnect/respawn/смене измерения. */
	public static void clear(ServerPlayer player) {
		if (player != null) {
			JOBS.remove(player.getUUID());
		}
	}

	private static int countItem(Player player, net.minecraft.world.item.Item item) {
		Inventory inventory = player.getInventory();
		int count = 0;
		for (int i = 0; i < inventory.getContainerSize(); i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() == item) {
				count += stack.getCount();
			}
		}
		return count;
	}

	private static void consumeItem(Player player, net.minecraft.world.item.Item item, int amount) {
		Inventory inventory = player.getInventory();
		int remaining = amount;
		for (int i = 0; i < inventory.getContainerSize() && remaining > 0; i++) {
			ItemStack stack = inventory.getItem(i);
			if (stack.getItem() == item) {
				int take = Math.min(remaining, stack.getCount());
				stack.shrink(take);
				remaining -= take;
			}
		}
	}
}
