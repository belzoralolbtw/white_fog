package com.whitefog.darkness.spawn;

import com.mojang.serialization.Codec;

import com.whitefog.WhiteFog;
import com.whitefog.WhiteFogAttachments;
import com.whitefog.darkness.shelter.ShelterProvider;
import com.whitefog.state.PlayerSurvivalState;

import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Серверный адаптер этапа 1.8 «Опасность тёмных участков».
 *
 * <p>Вызывается из единственного {@code END_SERVER_TICK} ({@link com.whitefog.server.WhiteFogServer});
 * второй серверный/игроцкий tick НЕ регистрируется. Каждые {@link DarkSpawnPolicy#PERIOD_TICKS}
 * тиков обходит loaded ticking chunks каждого не-Peaceful измерения и, если рядом (24..32 блока от
 * центра chunk) есть уязвимый игрок (exposure>=75), с детерминированным RNG пытается добавить
 * одного vanilla-моба (zombie/skeleton/spider/creeper). Новые EntityType не регистрируются.</p>
 *
 * <p>Marker модового моба хранится через Fabric Data Attachment API ({@code persistent} Codec),
 * который в 26.2 сериализуется инъекцией в {@code Entity#save}/{@code Entity#load}
 * (проверено javap по {@code fabric-data-attachment-api-v1}: {@code EntityMixin#writeEntityAttachments}
 * / {@code #readEntityAttachments}), т.е. переживает save/load без собственного миксина и без
 * {@code getPersistentData()}. Runtime-индекс loaded мобов восстанавливается событиями Fabric
 * {@code ServerEntityEvents.ENTITY_LOAD}/{@code ENTITY_UNLOAD} (в 26.2 это
 * {@code ServerLevel.EntityCallbacks#onTrackingStart/onTrackingEnd} — проверено javap).</p>
 *
 * <p>Ссылки (прочитано, адаптировано, не скопировано): {@code Glitchfiend/SereneSeasons}
 * {@code RandomUpdateHandler} (обход ticking chunks и близость игрока);
 * {@code VazkiiMods/Botania} {@code GaiaGuardianEntity} (последовательность
 * {@code finalizeSpawn(...)} + {@code addFreshEntity(...)}); {@code MinecraftForge}
 * {@code ForgeEventFactory#checkSpawnPosition} (пара {@code checkSpawnRules && checkSpawnObstruction}).</p>
 */
public final class DarkSpawnService {
	/** Оригин marker (совпадает со значением контракта). */
	public static final String ORIGIN = DarkMobState.DARK_AMBIENT_ORIGIN;

	private static final String TAG_ORIGIN = "origin";
	private static final String TAG_SPAWN_CHUNK = "spawn_chunk";
	private static final String TAG_UUID_MOST = "uuid_most";
	private static final String TAG_UUID_LEAST = "uuid_least";

	/** Codec marker: CompoundTag ↔ {@link DarkMobState.Marker}. */
	private static final Codec<DarkMobState.Marker> MARKER_CODEC =
			CompoundTag.CODEC.xmap(DarkSpawnService::markerFromTag, DarkSpawnService::markerToTag);

	/**
	 * Persistent-вложение, отличающее модового моба от vanilla. Регистрируется при загрузке класса
	 * (до старта сервера), поэтому marker доступен и для прочитанных из мира сущностей.
	 */
	public static final AttachmentType<DarkMobState.Marker> DARK_AMBIENT = AttachmentRegistry.create(
			WhiteFog.id("dark_ambient"),
			builder -> builder.persistent(MARKER_CODEC));

	private static final Map<DarkSpawnPolicy.MobKind, EntityType<? extends Mob>> TYPES = Map.of(
			DarkSpawnPolicy.MobKind.ZOMBIE, EntityTypes.ZOMBIE,
			DarkSpawnPolicy.MobKind.SKELETON, EntityTypes.SKELETON,
			DarkSpawnPolicy.MobKind.SPIDER, EntityTypes.SPIDER,
			DarkSpawnPolicy.MobKind.CREEPER, EntityTypes.CREEPER);

	private static boolean registered = false;

	private DarkSpawnService() {
	}

	/** Регистрирует вложение и обработчики загрузки/выгрузки сущностей. Идемпотентно. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		ServerEntityEvents.ENTITY_LOAD.register(DarkSpawnService::onEntityLoad);
		ServerEntityEvents.ENTITY_UNLOAD.register(DarkSpawnService::onEntityUnload);
		WhiteFog.LOGGER.info("White Fog: dark-ambient spawn service registered (stage 1.8, server-authoritative)");
	}

	// ------------------------------------------------------------------
	// Серверный тик
	// ------------------------------------------------------------------

	/** Единый серверный тик: обработка только на границе периода; внутри — по измерению. */
	public static void tickAll(MinecraftServer server) {
		if (server == null) {
			return;
		}
		try {
			if (Math.floorMod(server.getTickCount(), DarkSpawnPolicy.PERIOD_TICKS) != 0) {
				return;
			}
			for (ServerLevel level : server.getAllLevels()) {
				try {
					tickLevel(level);
				} catch (RuntimeException e) {
					WhiteFog.LOGGER.error("White Fog: dark spawn tick failed for dimension {}",
							level.dimension().identifier(), e);
				}
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: dark spawn tick-all failed", e);
		}
	}

	private static void tickLevel(ServerLevel level) {
		// Peaceful: дополнительных мобов нет.
		if (level.getDifficulty() == Difficulty.PEACEFUL) {
			return;
		}
		List<ServerPlayer> players = level.players();
		if (players.isEmpty()) {
			return;
		}
		List<DarkSpawnPolicy.PlayerInput> alive = new ArrayList<>();
		for (ServerPlayer player : players) {
			if (!player.isAlive()) {
				continue;
			}
			BlockPos feet = player.blockPosition();
			alive.add(new DarkSpawnPolicy.PlayerInput(player.getUUID(), player.getX(), player.getZ(),
					feet.getY(), true, isSurvivalOrAdventure(player), exposureOf(player)));
		}
		if (alive.isEmpty()) {
			return;
		}

		DarkMobState state = store(level).state();
		state.resetReservations();
		long bucket = DarkSpawnPolicy.gameTimeBucket(level.getLevelData().getGameTime());

		// Сначала собираем loaded ticking chunks, затем обрабатываем — не мутируем итерацию chunk-менеджера.
		List<ChunkPos> chunks = new ArrayList<>();
		level.getChunkSource().chunkMap.forEachBlockTickingChunk(chunk -> chunks.add(chunk.getPos()));
		for (ChunkPos chunk : chunks) {
			try {
				processChunk(level, state, chunk, alive, bucket);
			} catch (RuntimeException e) {
				WhiteFog.LOGGER.error("White Fog: dark spawn chunk {} failed", chunk, e);
			}
		}
	}

	/** Обрабатывает один chunk: target → cap → roll → тип → кандидат → spawn. */
	private static void processChunk(ServerLevel level, DarkMobState state, ChunkPos chunk,
			List<DarkSpawnPolicy.PlayerInput> alive, long bucket) {
		long chunkKey = chunk.pack();
		// Повторный bucket того же chunk не обрабатывается (в т.ч. после рестарта в том же timestamp).
		if (state.bucketProcessed(chunkKey, bucket)) {
			return;
		}
		DarkSpawnPolicy.PlayerInput target = DarkSpawnPolicy.nearestEligiblePlayer(
				alive, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
		if (target == null) {
			return;
		}
		// Cap проверяется ДО поиска кандидата.
		if (!DarkSpawnPolicy.capAllows(state.loadedChunkCount(chunkKey), state.loadedDimensionCount())) {
			return;
		}

		long seed = DarkSpawnPolicy.seed(level.getSeed(), chunk.x(), chunk.z(), bucket);
		DarkSpawnPolicy.Attempt attempt = DarkSpawnPolicy.attemptFor(seed);
		// Фиксируем bucket до исхода — второй запуск в том же bucket не выполняется.
		state.markAttempt(chunkKey, bucket);
		if (!DarkSpawnPolicy.permitsAttemptForRoll(attempt.roll())) {
			return;
		}

		DarkSpawnPolicy.MobKind kind = DarkSpawnPolicy.kindFor(state.successfulSpawns(chunkKey));
		DarkSpawnPolicy.Candidate candidate = DarkSpawnPolicy.selectCandidate(kind, target.feetY(),
				chunk.getMinBlockX(), chunk.getMinBlockZ(), attempt, alive, new LevelCandidateView(level));
		if (candidate == null) {
			return;
		}

		// Повторная проверка cap с учётом reservations; не даёт двум кандидатам перейти cap.
		if (!state.tryReserve(chunkKey)) {
			return;
		}
		try {
			Entity spawned = spawn(level, kind, candidate, chunkKey);
			if (spawned == null) {
				return;
			}
			state.recordSuccess(chunkKey);
			state.addLoaded(chunkKey, spawned.getUUID());
			WhiteFog.LOGGER.info(
					"WHITEFOG_DARK_SPAWN origin={} dimension={} chunk={},{} pos={},{},{} kind={} uuid={} "
							+ "targetExposure={} roll={} chunkMarked={} dimMarked={}",
					ORIGIN, level.dimension().identifier(), chunk.x(), chunk.z(),
					candidate.blockX(), candidate.blockY(), candidate.blockZ(), kind, spawned.getStringUUID(),
					target.exposure(), attempt.roll(), state.loadedChunkCount(chunkKey), state.loadedDimensionCount());
		} finally {
			state.releaseReservation(chunkKey);
		}
	}

	// ------------------------------------------------------------------
	// Создание vanilla-моба
	// ------------------------------------------------------------------

	/**
	 * Создаёт и добавляет моба той же vanilla-последовательностью, что и игра
	 * ({@code create → finalizeSpawn → checkSpawnRules/checkSpawnObstruction → world border →
	 * collision → addFreshEntity}); collision/world border/Peaceful не обходятся. Marker ставится
	 * до {@code addFreshEntity}, чтобы событие загрузки сразу проиндексировало моба.
	 */
	private static Entity spawn(ServerLevel level, DarkSpawnPolicy.MobKind kind,
			DarkSpawnPolicy.Candidate candidate, long spawnChunkKey) {
		EntityType<? extends Mob> type = candidateType(kind);
		Mob mob = type.create(level, EntitySpawnReason.MOB_SUMMONED);
		if (mob == null) {
			return null;
		}
		double x = candidate.blockX() + 0.5D;
		double y = candidate.blockY();
		double z = candidate.blockZ() + 0.5D;
		mob.snapTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
		mob.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, y, z)),
				EntitySpawnReason.MOB_SUMMONED, null);
		if (!mob.checkSpawnRules(level, EntitySpawnReason.MOB_SUMMONED)) {
			return null;
		}
		if (!mob.checkSpawnObstruction(level)) {
			return null;
		}
		AABB box = mob.getBoundingBox();
		if (!level.getWorldBorder().isWithinBounds(box)) {
			return null;
		}
		if (!level.noCollision(mob, box)) {
			return null;
		}
		DarkMobState.Marker marker = new DarkMobState.Marker(ORIGIN, spawnChunkKey, mob.getUUID());
		((AttachmentTarget) mob).setAttached(DARK_AMBIENT, marker);
		if (!level.addFreshEntity(mob)) {
			return null;
		}
		return mob;
	}

	// ------------------------------------------------------------------
	// Загрузка/выгрузка сущностей: runtime индекс
	// ------------------------------------------------------------------

	private static void onEntityLoad(Entity entity, ServerLevel level) {
		try {
			if (!isMarked(entity)) {
				return;
			}
			DarkMobState.Marker marker = ((AttachmentTarget) entity).getAttached(DARK_AMBIENT);
			if (marker == null || !ORIGIN.equals(marker.origin())) {
				return;
			}
			store(level).state().addLoaded(marker.spawnChunk(), entity.getUUID());
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: dark spawn entity-load index failed", e);
		}
	}

	private static void onEntityUnload(Entity entity, ServerLevel level) {
		try {
			if (!isMarked(entity)) {
				return;
			}
			DarkMobState.Marker marker = ((AttachmentTarget) entity).getAttached(DARK_AMBIENT);
			if (marker == null) {
				return;
			}
			// get(...) не создаёт SavedData во время выгрузки измерения.
			DarkSpawnStore store = level.getDataStorage().get(DarkSpawnStore.TYPE);
			if (store != null) {
				store.state().removeLoaded(marker.spawnChunk(), entity.getUUID());
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: dark spawn entity-unload index failed", e);
		}
	}

	/** Отличим ли моб от vanilla (есть модовый marker). */
	public static boolean isMarked(Entity entity) {
		return entity instanceof AttachmentTarget target && target.hasAttached(DARK_AMBIENT);
	}

	// ------------------------------------------------------------------
	// Хранилище и адаптеры
	// ------------------------------------------------------------------

	/** Persistent-хранилище попыток измерения. */
	public static DarkSpawnStore store(ServerLevel level) {
		return level.getDataStorage().computeIfAbsent(DarkSpawnStore.TYPE);
	}

	private static EntityType<? extends Mob> candidateType(DarkSpawnPolicy.MobKind kind) {
		return TYPES.get(kind);
	}

	private static boolean isSurvivalOrAdventure(ServerPlayer player) {
		GameType mode = player.gameMode();
		return mode == GameType.SURVIVAL || mode == GameType.ADVENTURE;
	}

	private static int exposureOf(ServerPlayer player) {
		PlayerSurvivalState state = WhiteFogAttachments.getOrCreate(player);
		return state.getLightExposure();
	}

	private static CompoundTag markerToTag(DarkMobState.Marker marker) {
		CompoundTag tag = new CompoundTag();
		tag.putString(TAG_ORIGIN, marker.origin() == null ? ORIGIN : marker.origin());
		tag.putLong(TAG_SPAWN_CHUNK, marker.spawnChunk());
		UUID uuid = marker.spawnUuid();
		tag.putLong(TAG_UUID_MOST, uuid == null ? 0L : uuid.getMostSignificantBits());
		tag.putLong(TAG_UUID_LEAST, uuid == null ? 0L : uuid.getLeastSignificantBits());
		return tag;
	}

	private static DarkMobState.Marker markerFromTag(CompoundTag tag) {
		String origin = tag.getStringOr(TAG_ORIGIN, "");
		long spawnChunk = tag.getLongOr(TAG_SPAWN_CHUNK, Long.MIN_VALUE);
		UUID uuid = new UUID(tag.getLongOr(TAG_UUID_MOST, 0L), tag.getLongOr(TAG_UUID_LEAST, 0L));
		return new DarkMobState.Marker(origin, spawnChunk, uuid);
	}

	/**
	 * Серверный (loaded-only) взгляд policy на мир. Ничего не грузит: chunk кандидата уже ticking,
	 * проверки идут через {@code getChunkNow}/{@code getBlockState}/{@code getBrightness}.
	 */
	private static final class LevelCandidateView implements DarkSpawnPolicy.CandidateView {
		private final ServerLevel level;

		LevelCandidateView(ServerLevel level) {
			this.level = level;
		}

		@Override
		public boolean isLoadedColumn(int blockX, int blockZ) {
			return level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4) != null;
		}

		@Override
		public boolean hasFullSolidFloor(int blockX, int floorY, int blockZ) {
			BlockPos pos = new BlockPos(blockX, floorY, blockZ);
			if (!level.isLoaded(pos)) {
				return false;
			}
			BlockState floor = level.getBlockState(pos);
			return floor.isCollisionShapeFullBlock(level, pos) && floor.isFaceSturdy(level, pos, Direction.UP);
		}

		@Override
		public boolean isAirAndClear(int blockX, int feetY, int blockZ, DarkSpawnPolicy.MobKind kind) {
			BlockPos feet = new BlockPos(blockX, feetY, blockZ);
			BlockPos head = feet.above();
			if (!level.isLoaded(feet) || !level.isLoaded(head)) {
				return false;
			}
			if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(head).isEmpty()) {
				return false;
			}
			if (!level.getBlockState(feet).isAir() || !level.getBlockState(head).isAir()) {
				return false;
			}
			AABB box = candidateType(kind).getSpawnAABB(blockX + 0.5D, feetY, blockZ + 0.5D);
			return level.noCollision(box);
		}

		@Override
		public int blockLight(int blockX, int feetY, int blockZ) {
			return level.getBrightness(LightLayer.BLOCK, new BlockPos(blockX, feetY, blockZ));
		}

		@Override
		public boolean isSheltered(int blockX, int feetY, int blockZ) {
			return ShelterProvider.isShelteredAt(level, new BlockPos(blockX, feetY, blockZ));
		}
	}
}
