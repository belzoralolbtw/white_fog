package com.whitefog.tests.station;

import java.util.HashMap;
import java.util.Map;

/**
 * Чистая логика серверно-авторитетной станции «Плоский камень» и камушек (этап 1.4).
 *
 * <p>Модель повторяет правила, которые затем реализуются в {@code src} (Fabric 26.2):
 * установка, снятие станции одним interaction, подбор камушка, recovery-взаимодействие
 * «2 cobblestone → 1 flat_stone за 100 тиков», однократность, revision/job guards,
 * полный инвентарь (pending drop), piston/explosion, save/load active job.</p>
 *
 * <p><b>Это НЕ runtime-proof.</b> Ни {@code BlockState}, ни {@code ItemStack}, ни block entity,
 * ни сеть здесь не проверяются — только инварианты логики.</p>
 */
public final class StationService {

	// --- id блоков/предметов (совпадают с src) ---
	public static final String FLAT_STONE = "white_fog:flat_stone";
	public static final String SMALL_STONE = "white_fog:small_stone";
	public static final String COBBLESTONE = "minecraft:cobblestone";
	public static final String STONE_SLAB = "minecraft:stone_slab";

	// --- утверждённые значения (синхронизированы с WhiteFogConfig этапа 1.4) ---
	/** Recovery «2 cobblestone → 1 flat_stone»: длительность в тиках. */
	public static final int RECOVERY_TICKS = 100;
	/** Recovery: стоимость в cobblestone. */
	public static final int RECOVERY_COST = 2;
	/** Cooldown подбора камушка на игрока, тиков. */
	public static final int SMALL_STONE_PICKUP_COOLDOWN_TICKS = 2;
	/** Задержка подбора у остатка, выброшенного при полном инвентаре, тиков. */
	public static final int PENDING_PICKUP_DELAY_TICKS = 10;
	/** schemaVersion block entity станции. */
	public static final int SCHEMA_VERSION = 1;

	/** Текст отказа для занятой станции (утверждён ТЗ этапа 1.4). */
	public static final String MESSAGE_BUSY = "Сначала забери материалы и результат";

	public enum InstallResult { PLACED, UNSUPPORTED, LIQUID, OCCUPIED, NO_PERMISSION, NO_ITEM }

	public enum RemoveResult { REMOVED, BUSY, NO_BLOCK }

	public enum PickupResult { PICKED, COOLDOWN, NO_BLOCK }

	public enum PushResult { BLOCKED, DESTROYED_ONE, NO_BLOCK }

	public enum RecoveryResult { STARTED, REJECTED, IN_PROGRESS, COMPLETED, CANCELLED }

	/** Активный recovery-job (world interaction, не блок-entity). */
	private static final class Recovery {
		final String dimension;
		final SimWorld.StationKey target;
		final double startX;
		final double startY;
		final double startZ;
		final float startHealth;
		final long startTick;

		Recovery(String dimension, SimWorld.StationKey target, double startX, double startY, double startZ,
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

	private final SimWorld world;
	private final Map<String, Recovery> recoveries = new HashMap<>();
	private final Map<String, Long> lastPickupTick = new HashMap<>();

	/** Счётчик отказов по занятости (для проверок). */
	public int busyRefusals;

	public StationService(SimWorld world) {
		this.world = world;
	}

	public void clearPlayer(PlayerRef player) {
		this.recoveries.remove(player.uuid);
		this.lastPickupTick.remove(player.uuid);
	}

	// ------------------------------------------------------------------
	// Установка
	// ------------------------------------------------------------------

	/**
	 * Установка блока предметом по верхней стороне полной твёрдой опоры.
	 *
	 * <p>Проверки выполняются ДО расхода предмета: неподходящее место не списывает предмет
	 * (см. критерий «unsupported placement не списывает предмет»).</p>
	 */
	public InstallResult install(PlayerRef player, SimWorld.StationKey key, String blockId,
			boolean supportSolid, boolean targetReplaceable, boolean targetLiquid, boolean mayPlace) {
		if (!supportSolid) {
			return InstallResult.UNSUPPORTED;
		}
		if (targetLiquid) {
			return InstallResult.LIQUID;
		}
		if (!targetReplaceable || this.world.has(key)) {
			return InstallResult.OCCUPIED;
		}
		if (!mayPlace) {
			return InstallResult.NO_PERMISSION;
		}
		if (!player.creative && !player.has(blockId, 1)) {
			return InstallResult.NO_ITEM;
		}
		this.world.put(key, blockId);
		if (!player.creative) {
			player.remove(blockId, 1);
		}
		return InstallResult.PLACED;
	}

	// ------------------------------------------------------------------
	// Снятие станции (Shift+ПКМ пустой рукой) и подбор камушка (ПКМ)
	// ------------------------------------------------------------------

	/**
	 * Снятие станции за один interaction. Занятую станцию (job/escrow/output) не снимает
	 * и отвечает {@link #MESSAGE_BUSY}. Пустую — удаляет и выдаёт ровно один предмет.
	 */
	public RemoveResult removeStation(PlayerRef player, SimWorld.StationKey key) {
		SimWorld.Block block = this.world.get(key);
		if (block == null || !FLAT_STONE.equals(block.id)) {
			return RemoveResult.NO_BLOCK;
		}
		if (block.isBusy()) {
			this.busyRefusals++;
			this.world.message(MESSAGE_BUSY);
			return RemoveResult.BUSY;
		}
		this.world.removeRaw(key);
		giveOne(player, key, FLAT_STONE);
		return RemoveResult.REMOVED;
	}

	/** Подбор камушка обычным ПКМ (обрабатывается только main-hand) с cooldown 2 тика. */
	public PickupResult pickupSmallStone(PlayerRef player, SimWorld.StationKey key, long now) {
		SimWorld.Block block = this.world.get(key);
		if (block == null || !SMALL_STONE.equals(block.id)) {
			return PickupResult.NO_BLOCK;
		}
		Long last = this.lastPickupTick.get(player.uuid);
		if (last != null && now - last < SMALL_STONE_PICKUP_COOLDOWN_TICKS) {
			return PickupResult.COOLDOWN;
		}
		this.lastPickupTick.put(player.uuid, now);
		// Однократность: удаляем ДО выдачи; повторный click в том же тике уже увидит air.
		this.world.removeRaw(key);
		giveOne(player, key, SMALL_STONE);
		return PickupResult.PICKED;
	}

	/** Ровно один предмет: в инвентарь, при полном — pending-drop с задержкой 10 тиков. */
	private void giveOne(PlayerRef player, SimWorld.StationKey key, String itemId) {
		if (player.add(itemId, 1)) {
			this.world.givenToInventory++;
		} else {
			this.world.spawnDrop(key, itemId, 1, PENDING_PICKUP_DELAY_TICKS);
		}
	}

	// ------------------------------------------------------------------
	// Piston / explosion
	// ------------------------------------------------------------------

	/** Поршень: flat_stone неподвижен (PushReaction.BLOCK), small_stone разрушается с ровно 1 дропом. */
	public PushResult pistonPush(SimWorld.StationKey key) {
		SimWorld.Block block = this.world.get(key);
		if (block == null) {
			return PushResult.NO_BLOCK;
		}
		if (FLAT_STONE.equals(block.id)) {
			// Не двигаем станцию (в т.ч. с job): ноль изменений, ноль выдачи.
			return PushResult.BLOCKED;
		}
		this.world.removeRaw(key);
		this.world.spawnDrop(key, block.id, 1, 0);
		return PushResult.DESTROYED_ONE;
	}

	/** Взрыв/потеря опоры: block loot ровно один раз, без второго вызова на remove callback. */
	public PushResult explosion(SimWorld.StationKey key) {
		SimWorld.Block block = this.world.get(key);
		if (block == null) {
			// Повторный взрыв по той же позиции — второй выдачи нет.
			return PushResult.NO_BLOCK;
		}
		this.world.removeRaw(key);
		this.world.spawnDrop(key, block.id, 1, 0);
		return PushResult.DESTROYED_ONE;
	}

	// ------------------------------------------------------------------
	// Recovery: 2 cobblestone -> 1 flat_stone за 100 тиков
	// ------------------------------------------------------------------

	/**
	 * Запуск recovery-взаимодействия (Shift+ПКМ cobblestone по твёрдой земле при наличии
	 * второго cobblestone). Предметы НЕ списываются на старте — только при успешном завершении
	 * (атомарность: нельзя списывать до подтверждения возможности завершить действие).
	 */
	public RecoveryResult recoveryStart(PlayerRef player, SimWorld.StationKey groundKey, String mainHandItem,
			int totalCobblestone, boolean supportSolid, boolean targetReplaceable, boolean targetLiquid, long now) {
		if (!COBBLESTONE.equals(mainHandItem)) {
			return RecoveryResult.REJECTED;
		}
		if (totalCobblestone < RECOVERY_COST || !player.has(COBBLESTONE, RECOVERY_COST)) {
			return RecoveryResult.REJECTED;
		}
		if (!supportSolid || !targetReplaceable || targetLiquid) {
			return RecoveryResult.REJECTED;
		}
		SimWorld.StationKey target = above(groundKey);
		this.recoveries.put(player.uuid,
				new Recovery(player.dimension, target, player.x, player.y, player.z, player.health, now));
		return RecoveryResult.STARTED;
	}

	/** Тик recovery-job. Движение/урон/смерть отменяют задачу (расхода не было → возврат тривиален). */
	public RecoveryResult recoveryTick(PlayerRef player, long now) {
		Recovery r = this.recoveries.get(player.uuid);
		if (r == null) {
			return RecoveryResult.REJECTED;
		}
		if (!player.dimension.equals(r.dimension) || player.health < r.startHealth || player.health <= 0.0F) {
			this.recoveries.remove(player.uuid);
			return RecoveryResult.CANCELLED;
		}
		double dx = player.x - r.startX;
		double dy = player.y - r.startY;
		double dz = player.z - r.startZ;
		if (dx * dx + dy * dy + dz * dz > 0.05) {
			// Движение игрока отменяет задачу. Ничего не списано — предметы остаются у игрока.
			this.recoveries.remove(player.uuid);
			return RecoveryResult.CANCELLED;
		}
		if (now - r.startTick < RECOVERY_TICKS) {
			return RecoveryResult.IN_PROGRESS;
		}
		// Завершение: перепроверяем входы (атомарно) и цель.
		if (!player.has(COBBLESTONE, RECOVERY_COST)) {
			this.recoveries.remove(player.uuid);
			return RecoveryResult.CANCELLED;
		}
		this.recoveries.remove(player.uuid);
		player.remove(COBBLESTONE, RECOVERY_COST);
		this.world.put(r.target, FLAT_STONE);
		return RecoveryResult.COMPLETED;
	}

	public boolean hasRecovery(PlayerRef player) {
		return this.recoveries.containsKey(player.uuid);
	}

	// ------------------------------------------------------------------
	// Save / load active job
	// ------------------------------------------------------------------

	/** Сериализует block entity станции (модель NBT через ValueOutput/ValueInput). */
	public String saveBlock(SimWorld.Block block) {
		return "id=" + block.id
				+ ";schema=" + block.schemaVersion
				+ ";revision=" + block.revision
				+ ";job=" + (block.jobOwner == null ? "" : block.jobOwner)
				+ ";escrow=" + block.escrow
				+ ";output=" + block.output
				+ ";mode=" + block.mode
				+ ";progress=" + block.jobProgress
				+ ";required=" + block.jobRequired;
	}

	/** Восстанавливает block entity из строки (модель NBT load). */
	public SimWorld.Block loadBlock(String data) {
		Map<String, String> map = new HashMap<>();
		for (String part : data.split(";")) {
			int eq = part.indexOf('=');
			if (eq > 0) {
				map.put(part.substring(0, eq), part.substring(eq + 1));
			}
		}
		SimWorld.Block block = new SimWorld.Block(map.getOrDefault("id", FLAT_STONE));
		block.schemaVersion = parseInt(map.get("schema"), SCHEMA_VERSION);
		block.revision = parseInt(map.get("revision"), 0L);
		String job = map.getOrDefault("job", "");
		block.jobOwner = job.isEmpty() ? null : job;
		block.escrow = parseInt(map.get("escrow"), 0);
		block.output = parseInt(map.get("output"), 0);
		block.mode = map.getOrDefault("mode", "CRAFT");
		block.jobProgress = parseInt(map.get("progress"), 0);
		block.jobRequired = parseInt(map.get("required"), 0);
		return block;
	}

	private static int parseInt(String value, int fallback) {
		try {
			return value == null ? fallback : Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	private static long parseInt(String value, long fallback) {
		try {
			return value == null ? fallback : Long.parseLong(value);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** Детерминированный вариант модели камушка по позиции (не влияет на loot). */
	public static boolean smallStoneVariant(SimWorld.StationKey key) {
		return ((key.x() ^ key.z()) & 1) == 0;
	}

	private static SimWorld.StationKey above(SimWorld.StationKey key) {
		return new SimWorld.StationKey(key.dimension(), key.x(), key.y() + 1, key.z());
	}
}
