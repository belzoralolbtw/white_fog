package com.whitefog.darkness.light;
import com.mojang.serialization.Codec;
import com.whitefog.WhiteFog;
import com.whitefog.content.menu.LightSourceMenu;
import com.whitefog.darkness.light.LightFuelPolicy.SourceKind;
import com.whitefog.darkness.light.LightSourceStore.Record;
import com.whitefog.network.LightRefuelPayload;
import com.whitefog.network.LightRefuelResultPayload;
import com.whitefog.network.LightSourceSnapshotPayload;
import com.whitefog.network.SourcePanelPayload;

import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Серверный сервис гаснущих источников света (этап 1.6).
 *
 * <p>Вызывается из единственного {@code END_SERVER_TICK} в
 * {@link com.whitefog.server.WhiteFogServer} (второй player tick НЕ регистрируется). Отвечает за:</p>
 * <ul>
 *     <li>ленивую инициализацию чанка (генерационный бонус ровно один раз на чанк);</li>
 *     <li>серверный отсчёт топлива loaded+lit источников (1→0 сразу гасит эмиссию/частицы);</li>
 *     <li>запись при установке предмета (компонент {@code white_fog:light_fuel}, без бонуса);</li>
 *     <li>refuel/light job 20 тиков с серверной валидацией и атомарным списанием;</li>
 *     <li>поиск ближайшего источника (nearest) и S2C-снимки для Work Panel;</li>
 *     <li>дозапись remaining в единственный штатный drop без дополнительных предметов.</li>
 * </ul>
 *
 * <p>Ссылки: реальные deobf-классы 26.2 ({@code TorchBlock}/{@code WallTorchBlock}/{@code LanternBlock}/
 * {@code CampfireBlock}, {@code SavedDataStorage}, {@code ServerChunkEvents}) прочитаны через javap;
 * удалённый open-source поиск в этой среде недоступен (нет сети), поэтому источник правил — утверждённый
 * контракт ROADMAP_STEPS.md (этап 1.6). Для разделения состояния источника и предмета сверено
 * с открытым LambDynamicLights: {@code api/src/main/java/dev/lambdaurora/lambdynlights/api/item/ItemLightSource.java}
 * (https://github.com/LambdAurora/LambDynamicLights/blob/1.21.11/api/src/main/java/dev/lambdaurora/lambdynlights/api/item/ItemLightSource.java).
 * Здесь сохранение топлива адаптировано к серверному block/item round-trip.</p>
 */
public final class LightSourceService {
	public record RuntimeDiagnostic(boolean refuelActive, BlockPos refuelPos, long refuelElapsed,
			boolean menuOpen, BlockPos menuPos, int menuStatus) { }

	/**
	 * Runtime refuel job: одна операция на игрока (не сохраняется). {@code Заправить} идёт 20 тиков
	 * и НИКОГДА не меняет {@code lit}; зажигание — мгновенное отдельное действие {@code Зажечь}.
	 */
	private record RefuelJob(ResourceKey<Level> dimension, BlockPos pos, UUID sourceUuid, ItemStack fuelTemplate,
			LightFuelPolicy.Fuel fuel, int addition, long startTick) {
	}

	/** Снимок ближайшего источника. */
	public record Snapshot(BlockPos pos, UUID sourceUuid, long revision, int remaining, boolean lit, SourceKind kind) {
	}

	/** Результат inspect (по контракту ROADMAP: uuid, remaining, lit, revision). */
	public record Inspect(UUID sourceUuid, int remainingTicks, boolean lit, long revision) {
	}

	/** Полное состояние источника для панели/экрана (этап поверх 1.6). */
	public record PanelInfo(boolean present, BlockPos pos, UUID sourceUuid, long revision, int remaining,
			boolean lit, SourceKind kind, boolean managedByPost, int capacity) {
	}

	/** Сигнатура открытой панели (для отправки обновления только при изменении). */
	private record PanelSignature(BlockPos pos, long revision, int remaining, boolean lit) {
	}

	/** Ожидаемая установка, зафиксированная в {@code getStateForPlacement} для commit в {@code BlockItem.place}. */
	private record Pending(ResourceKey<Level> dimension, BlockPos pos, SourceKind kind, String blockId, int remaining) {
	}

	/** Фактическая цель установки: позиция реально поставленного блока + его точный block id. */
	private record PlacementTarget(BlockPos pos, String blockId) {
	}

	private static final Map<UUID, RefuelJob> JOBS = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> LAST_SEQUENCE = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> FULL_MESSAGE_COOLDOWN = new ConcurrentHashMap<>();
	private static final Map<UUID, LightSourceSnapshotPayload> LAST_SNAPSHOT = new ConcurrentHashMap<>();
	/** Сигнатура последнего отправленного состояния открытой панели источника. */
	private static final Map<UUID, PanelSignature> OPEN_PANEL_SIG = new ConcurrentHashMap<>();
	/** Тик последней отправки открытой панели (throttle, чтобы не слать каждый тик). */
	private static final Map<UUID, Long> OPEN_PANEL_LAST_SEND = new ConcurrentHashMap<>();

	private static final ThreadLocal<Pending> PENDING_PLACEMENT = new ThreadLocal<>();
	private static final Map<ServerLevel, LightSourceStore> KNOWN_STORES = new java.util.WeakHashMap<>();
	private static final ThreadLocal<DropState> PENDING_DROP = new ThreadLocal<>();
	public record DropContext(ServerLevel level, BlockPos pos, SourceKind kind, BlockState state, DropContext parent) { }
	private record DropState(ServerLevel level, BlockPos pos, SourceKind kind, BlockState state, DropContext parent) { }

	private static boolean registered = false;
	/** Последний обработанный серверный тик (идемпотентность tickAll). */
	private static int lastTickedServerTick = Integer.MIN_VALUE;

	private LightSourceService() {
	}

	/** Регистрация сервиса (логирование). Идемпотентна. */
	public static void register() {
		if (registered) {
			return;
		}
		registered = true;
		// Касаемся компонента — это регистрирует DataComponentType до первого использования.
		LightFuelComponent.register();
		WhiteFog.LOGGER.info(
				"White Fog: light sources service registered (stage 1.6, server-authoritative countdown)");
		// Диагностический self-test после старта сервера (доказательство наличия свойства/эмиссии).
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STARTED.register(LightSourceService::logSelfTest);
	}

	/** Пишет строку {@code WHITEFOG_LIGHT_SELFTEST ...} — доказательство для smoke-логов. */
	private static void logSelfTest(MinecraftServer server) {
		try {
			boolean torchProp = net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState()
					.hasProperty(LightSourceBlocks.WHITE_FOG_LIT);
			boolean wallProp = net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState()
					.hasProperty(LightSourceBlocks.WHITE_FOG_LIT);
			boolean soulTorchProp = net.minecraft.world.level.block.Blocks.SOUL_TORCH.defaultBlockState()
					.hasProperty(LightSourceBlocks.WHITE_FOG_LIT);
			boolean lanternProp = net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState()
					.hasProperty(LightSourceBlocks.WHITE_FOG_LIT);
			boolean soulLanternProp = net.minecraft.world.level.block.Blocks.SOUL_LANTERN.defaultBlockState()
					.hasProperty(LightSourceBlocks.WHITE_FOG_LIT);
			boolean redstoneProp = net.minecraft.world.level.block.Blocks.REDSTONE_TORCH.defaultBlockState()
					.hasProperty(LightSourceBlocks.WHITE_FOG_LIT);
			int litEmission = net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState().getLightEmission();
			int unlitEmission = net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState()
					.setValue(LightSourceBlocks.WHITE_FOG_LIT, false).getLightEmission();
			boolean ok = torchProp && wallProp && soulTorchProp && lanternProp && soulLanternProp
					&& !redstoneProp && litEmission == 14 && unlitEmission == 0;
			WhiteFog.LOGGER.info(
					"WHITEFOG_LIGHT_SELFTEST torch={} wall={} soulTorch={} lantern={} soulLantern={} redstone={} "
							+ "litEmission={} unlitEmission={} status={}",
					torchProp, wallProp, soulTorchProp, lanternProp, soulLanternProp, redstoneProp,
					litEmission, unlitEmission, ok ? "SUCCESS" : "FAILURE");
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("WHITEFOG_LIGHT_SELFTEST status=FAILURE (exception)", e);
		}
		fuelCodecSelfTest(server);
		productionRoundTripSelfTest(server);
	}

	private static void productionRoundTripSelfTest(MinecraftServer server) {
		int assertions = 0;
		try {
			for (BlockState fixture : List.of(net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(),
					net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState(),
					net.minecraft.world.level.block.Blocks.SOUL_TORCH.defaultBlockState(),
					net.minecraft.world.level.block.Blocks.SOUL_WALL_TORCH.defaultBlockState(),
					net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState(),
					net.minecraft.world.level.block.Blocks.SOUL_LANTERN.defaultBlockState())) {
				SourceKind kind = LightSourceBlocks.kind(fixture);
				for (int variant = 0; variant < 3; variant++) {
					boolean burning = variant == 2;
					int remaining = variant == 0 ? 0 : 1234;
					BlockState state = LightSourceBlocks.withLit(fixture, burning);
					ItemStack item = new ItemStack(kind == SourceKind.TORCH ? net.minecraft.world.item.Items.TORCH
							: kind == SourceKind.SOUL_TORCH ? net.minecraft.world.item.Items.SOUL_TORCH : state.getBlock().asItem());
					LightSourceStore isolated = new LightSourceStore();
					BlockPos pos = new BlockPos(1, 2, 3);
					isolated.put(pos, new Record(UUID.randomUUID(), LightSourceBlocks.blockId(state), remaining, 1L, false));
					DropContext parent = pushDropContext(server.overworld(), pos, state);
					boolean dropped;
					try {
						dropped = decorateDrop(isolated, pos, resolveDropState(server.overworld(), pos,
								net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()), item);
						assertions++;
						if (decorateDrop(isolated, pos, state, item.copy())) throw new IllegalStateException("duplicate drop");
						assertions++;
						if (!resolveDropState(server.getLevel(Level.NETHER), pos,
								net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()).isAir()) throw new IllegalStateException("dimension context");
					} finally { restoreDropContext(parent); }
					assertions++;
					if (currentContext() != null) throw new IllegalStateException("context cleanup");
					assertions++;
					if (!dropped || LightFuelComponent.read(item).remainingTicks() != remaining
							|| LightFuelComponent.read(item).lit() != burning) throw new IllegalStateException("drop " + kind);
					BlockState placed = LightFuelRoundTrip.applyItem(fixture, item);
					assertions++;
					if (LightSourceBlocks.isLit(placed) != burning) throw new IllegalStateException("place " + kind);
				}
			}
			assertions++;
			if (!streamRoundTrip(server, new LightFuelComponent.LightFuel(4321, false)))
				throw new IllegalStateException("positive-remaining unlit stream codec");
			assertions++;
			if (!streamRoundTrip(server, new LightFuelComponent.LightFuel(0, false)))
				throw new IllegalStateException("empty stream codec");
			BlockPos root = new BlockPos(11, 12, 13), neighbor = root.above();
			BlockState unlit = LightSourceBlocks.withLit(net.minecraft.world.level.block.Blocks.TORCH.defaultBlockState(), false);
			DropContext outer = pushDropContext(server.overworld(), root, unlit);
			try {
				DropContext nested = pushDropContext(server.overworld(), neighbor,
						net.minecraft.world.level.block.Blocks.LANTERN.defaultBlockState());
				try { throw new IllegalStateException("intentional fixture exception"); }
				catch (IllegalStateException expected) { }
				finally { restoreDropContext(nested); }
				assertions++;
				if (resolveDropState(server.overworld(), root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()) != unlit)
					throw new IllegalStateException("nested context restoration");
				assertions++;
				if (resolveDropState(server.overworld(), neighbor, unlit) != unlit)
					throw new IllegalStateException("neighbor fallback suppressed");
				LightSourceStore isolated = new LightSourceStore();
				isolated.put(root, new Record(UUID.randomUUID(), LightSourceBlocks.blockId(unlit), 555, 1L, false));
				assertions++;
				if (decorateDrop(isolated, root, unlit, new ItemStack(net.minecraft.world.item.Items.STICK)) || isolated.get(root) == null)
					throw new IllegalStateException("unrelated item consumed record");
				assertions++;
				if (decorateDrop(isolated, root, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),
						new ItemStack(net.minecraft.world.item.Items.TORCH))) throw new IllegalStateException("air manufactured fuel");
				assertions++;
				if (decorateDrop(isolated, root, net.minecraft.world.level.block.Blocks.WALL_TORCH.defaultBlockState(),
						new ItemStack(net.minecraft.world.item.Items.TORCH))) throw new IllegalStateException("expected block id mismatch");
			} finally { restoreDropContext(outer); }
			assertions++;
			if (currentContext() != null) throw new IllegalStateException("exception context leaked");
			WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ROUNDTRIP_SELFTEST assertions={} fixtures=standing,wall,soul,lantern "
					+ "dropAndPlacement=true streamUnlitPositive=true status=SUCCESS", assertions);
		} catch (RuntimeException error) {
			WhiteFog.LOGGER.error("WHITEFOG_LIGHT_ROUNDTRIP_SELFTEST assertions={} status=FAILURE", assertions, error);
		}
	}

	/**
	 * Runtime-доказательство нового item-компонента {@code white_fog:light_fuel} (remaining+lit):
	 * round-trip пары через {@code NbtOps} И через сетевой {@code STREAM_CODEC} (тот же кодек, что
	 * использует ItemStack при синхронизации с клиентом), обратная совместимость со старым
	 * int-компонентом (int {@code >0} -> {@code lit=true}, {@code 0} -> {@code false},
	 * негатив -> 0/false), а также компаунд без {@code lit} -> {@code lit=true}. Пишет строку
	 * {@code WHITEFOG_LIGHT_FUEL_CODEC_SELFTEST ... status=...}.
	 */
	private static void fuelCodecSelfTest(MinecraftServer server) {
		try {
			boolean pairRoundTrip = codecRoundTrip(new LightFuelComponent.LightFuel(12_000, true))
					&& codecRoundTrip(new LightFuelComponent.LightFuel(0, false))
					&& codecRoundTrip(new LightFuelComponent.LightFuel(4_800, false));
			// Сетевой круг: именно этот StreamCodec уходит клиенту в составе ItemStack. Если бы он
			// был сломан, клиент получил бы пустой/повреждённый стек (предмет «исчезал» бы из руки).
			boolean streamRoundTrip = server != null
					&& streamRoundTrip(server, new LightFuelComponent.LightFuel(12_000, true))
					&& streamRoundTrip(server, new LightFuelComponent.LightFuel(0, false))
					&& streamRoundTrip(server, new LightFuelComponent.LightFuel(4_800, true));
			LightFuelComponent.LightFuel fromInt = decodeLegacyInt(5_000);
			boolean migratePositive = fromInt != null && fromInt.remainingTicks() == 5_000 && fromInt.lit();
			LightFuelComponent.LightFuel fromZero = decodeLegacyInt(0);
			boolean migrateZero = fromZero != null && fromZero.remainingTicks() == 0 && !fromZero.lit();
			LightFuelComponent.LightFuel fromNegative = decodeLegacyInt(-7);
			boolean migrateNegative = fromNegative != null && fromNegative.remainingTicks() == 0
					&& !fromNegative.lit();
			CompoundTag legacyCompound = new CompoundTag();
			legacyCompound.putInt("remaining", 100);
			LightFuelComponent.LightFuel fromCompound = LightFuelComponent.LightFuel.CODEC
					.parse(NbtOps.INSTANCE, legacyCompound).result().orElse(null);
			boolean defaultLit = fromCompound != null && fromCompound.remainingTicks() == 100 && fromCompound.lit();
			boolean ok = pairRoundTrip && streamRoundTrip && migratePositive && migrateZero && migrateNegative
					&& defaultLit;
			WhiteFog.LOGGER.info(
					"WHITEFOG_LIGHT_FUEL_CODEC_SELFTEST pairRoundTrip={} streamRoundTrip={} migratePositive={} "
							+ "migrateZero={} migrateNegative={} defaultLit={} status={}",
					pairRoundTrip, streamRoundTrip, migratePositive, migrateZero, migrateNegative, defaultLit,
					ok ? "SUCCESS" : "FAILURE");
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("WHITEFOG_LIGHT_FUEL_CODEC_SELFTEST status=FAILURE (exception)", e);
		}
	}

	/**
	 * Круговой прогон {@link LightFuelComponent.LightFuel#STREAM_CODEC} через настоящий
	 * {@link RegistryFriendlyByteBuf} (тот же сетевой кодек, что и у item-компонента).
	 */
	private static boolean streamRoundTrip(MinecraftServer server, LightFuelComponent.LightFuel fuel) {
		io.netty.buffer.ByteBuf raw = io.netty.buffer.Unpooled.buffer();
		try {
			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(raw, server.registryAccess());
			LightFuelComponent.LightFuel.STREAM_CODEC.encode(buf, fuel);
			buf.readerIndex(0);
			LightFuelComponent.LightFuel decoded = LightFuelComponent.LightFuel.STREAM_CODEC.decode(buf);
			return fuel.equals(decoded);
		} finally {
			raw.release();
		}
	}

	private static boolean codecRoundTrip(LightFuelComponent.LightFuel fuel) {
		Tag encoded = LightFuelComponent.LightFuel.CODEC.encodeStart(NbtOps.INSTANCE, fuel).result().orElse(null);
		if (encoded == null) {
			return false;
		}
		LightFuelComponent.LightFuel decoded = LightFuelComponent.LightFuel.CODEC
				.parse(NbtOps.INSTANCE, encoded).result().orElse(null);
		return fuel.equals(decoded);
	}

	private static LightFuelComponent.LightFuel decodeLegacyInt(int value) {
		Tag tag = Codec.INT.encodeStart(NbtOps.INSTANCE, value).result().orElse(null);
		if (tag == null) {
			return null;
		}
		return LightFuelComponent.LightFuel.CODEC.parse(NbtOps.INSTANCE, tag).result().orElse(null);
	}

	// ------------------------------------------------------------------
	// Хранилище
	// ------------------------------------------------------------------

	/** Хранилище источников для измерения (per-dimension SavedData). */
	public static LightSourceStore store(ServerLevel level) {
		LightSourceStore store = level.getDataStorage().computeIfAbsent(LightSourceStore.TYPE);
		KNOWN_STORES.put(level, store);
		return store;
	}

	/** Read-only refuel/menu runtime state for dev diagnostics. */
	public static RuntimeDiagnostic diagnostics(ServerPlayer player) {
		RefuelJob job = JOBS.get(player.getUUID());
		PanelSignature panel = OPEN_PANEL_SIG.get(player.getUUID());
		long now = player.level().getServer().getTickCount();
		return new RuntimeDiagnostic(job != null, job == null ? null : job.pos(),
			job == null ? 0L : Math.max(0L, now - job.startTick()), panel != null,
			panel == null ? null : panel.pos(), panel == null ? -1 : (int) panel.revision());
	}

	public static boolean hasActiveRefuel(ServerPlayer player) { return JOBS.containsKey(player.getUUID()); }

	// ------------------------------------------------------------------
	// Ленивая инициализация чанка
	// ------------------------------------------------------------------

	/** Инициализирует чанк, если он ещё не помечен (генерационный scan ровно один раз). */
	private static void initializeChunk(ServerLevel level, LevelChunk chunk, LightSourceStore store) {
		long key = chunk.getPos().pack();
		if (store.isChunkInitialized(key)) {
			return;
		}
		store.markChunkInitialized(key);
		chunk.findBlocks(LightSourceBlocks::isManaged, (pos, state) -> {
			BlockPos immutable = pos.immutable();
			if (store.get(immutable) != null) {
				return;
			}
			SourceKind kind = LightSourceBlocks.kind(state);
			if (kind == null) {
				return;
			}
			int bonus = LightFuelPolicy.generatedBonusTicks(kind, LightSourceBlocks.isLit(state));
			store.put(immutable, new Record(UUID.randomUUID(), LightSourceBlocks.blockId(state), bonus, 1L, false));
		});
	}

	/** Гарантирует, что чанк позиции просканирован (для интеракций/тика вне CHUNK_LOAD). */
	public static LightSourceStore ensureChunkInitialized(ServerLevel level, BlockPos pos) {
		LightSourceStore store = store(level);
		long key = net.minecraft.world.level.ChunkPos.containing(pos).pack();
		if (!store.isChunkInitialized(key)) {
			initializeChunk(level, level.getChunkAt(pos), store);
		}
		return store;
	}

	/**
	 * Проектный API структурам: инициализирует источник с заданным запасом (bonus-эквивалент).
	 * {@code managedByPost=true} запрещает refuel (пост — единственный владелец countdown).
	 */
	public static void initialize(ServerLevel level, BlockPos pos, int ticks, boolean managedByPost) {
		BlockState state = level.getBlockState(pos);
		SourceKind kind = LightSourceBlocks.kind(state);
		if (kind == null) {
			return;
		}
		int remaining = Math.max(0, Math.min(ticks, LightSourceBlocks.capacity(kind)));
		LightSourceStore store = store(level);
		store.put(pos.immutable(), new Record(UUID.randomUUID(), LightSourceBlocks.blockId(state), remaining, 1L,
				managedByPost));
		level.setBlock(pos, LightSourceBlocks.withLit(state, remaining > 0), Block.UPDATE_ALL);
	}

	/** Проектный API: читает состояние источника без изменения ({@code uuid, remaining, lit, revision}). */
	public static Optional<Inspect> inspect(ServerLevel level, BlockPos pos) {
		LightSourceStore store = store(level);
		Record record = store.get(pos);
		if (record == null) {
			return Optional.empty();
		}
		BlockState state = level.getBlockState(pos);
		if (!LightSourceBlocks.isManaged(state)
				|| !LightSourceBlocks.matchesExpected(state, record.expectedBlockId())) {
			return Optional.empty();
		}
		return Optional.of(new Inspect(record.sourceUuid(), record.remainingTicks(), LightSourceBlocks.isLit(state),
				record.revision()));
	}

	/** Fabric CHUNK_LOAD: ленивая инициализация при первом доступе к чанку. */
	public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
		try {
			if (!loggedFirstChunkInit) {
				loggedFirstChunkInit = true;
				WhiteFog.LOGGER.info("White Fog: light chunk init first chunk {} in {}",
						chunk.getPos(), level.dimension().identifier());
			}
			initializeChunk(level, chunk, store(level));
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: light chunk init failed at {}", chunk.getPos(), e);
		}
	}

	private static boolean loggedFirstChunkInit = false;

	// ------------------------------------------------------------------
	// Тик
	// ------------------------------------------------------------------

	/** Тикает источники всех измерений, затем job-ы, затем шлёт снимки Work Panel. */
	public static void tickAll(MinecraftServer server) {
		// Защита от повторного вызова на том же серверном тике (идемпотентность).
		int tick = server.getTickCount();
		if (tick == lastTickedServerTick) {
			return;
		}
		lastTickedServerTick = tick;
		for (ServerLevel level : server.getAllLevels()) {
			try {
				tickLevel(level);
			} catch (RuntimeException e) {
				WhiteFog.LOGGER.error("White Fog: light source tick failed in {}", level.dimension(), e);
			}
		}
		tickJobs(server);
		sendSnapshots(server);
		ensureChunksAroundPlayers(server);
	}

	/**
	 * Резервная ленивая инициализация чанков рядом с игроками (раз в 20 тиков).
	 *
	 * <p>{@code ServerChunkEvents.CHUNK_LOAD} срабатывает при переходе чанка в FULL (игрок рядом);
	 * этот проход гарантирует инициализацию уже загруженных чанков вокруг игроков (например, при
	 * телепорте/подгрузке), не делая полного скана мира: флаг инициализации хранится в store и
	 * проверяется дешёвой операцией. Сканируется только неинициализированный загруженный чанк.</p>
	 */
	private static void ensureChunksAroundPlayers(MinecraftServer server) {
		if (server.getTickCount() % 20 != 0) {
			return;
		}
		for (ServerLevel level : server.getAllLevels()) {
			List<ServerPlayer> players = level.players();
			if (players.isEmpty()) {
				continue;
			}
			LightSourceStore store = store(level);
			for (ServerPlayer player : players) {
				BlockPos origin = player.blockPosition();
				int centerX = origin.getX() >> 4;
				int centerZ = origin.getZ() >> 4;
				for (int dx = -CHUNK_SCAN_RADIUS; dx <= CHUNK_SCAN_RADIUS; dx++) {
					for (int dz = -CHUNK_SCAN_RADIUS; dz <= CHUNK_SCAN_RADIUS; dz++) {
						int cx = centerX + dx;
						int cz = centerZ + dz;
						if (store.isChunkInitialized(net.minecraft.world.level.ChunkPos.pack(cx, cz))) {
							continue;
						}
						LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
						if (chunk != null) {
							initializeChunk(level, chunk, store);
						}
					}
				}
			}
		}
	}

	/** Радиус прохода инициализации чанков вокруг игрока (в чанках). */
	private static final int CHUNK_SCAN_RADIUS = 4;

	/** Один тик измерений: loaded+lit remaining--; 1→0 немедленно гасит (light engine обновляется setBlock). */
	private static void tickLevel(ServerLevel level) {
		LightSourceStore store = store(level);
		for (Map.Entry<BlockPos, Record> entry : store.entries()) {
			BlockPos pos = entry.getKey();
			Record record = entry.getValue();
			if (!level.isLoaded(pos)) {
				continue; // unload останавливает ticking; elapsed world time не вычитается
			}
			BlockState state = level.getBlockState(pos);
			if (!LightSourceBlocks.isManaged(state)
					|| !LightSourceBlocks.matchesExpected(state, record.expectedBlockId())) {
				// Источник снят/заменён: запись удаляем (топливо теряется, если штатный путь не дал предмет).
				store.remove(pos);
				continue;
			}
			if (!LightSourceBlocks.isLit(state)) {
				continue; // погасший источник с запасом не тратит топливо
			}
			int remaining = record.remainingTicks() - 1;
			if (remaining <= 0) {
				level.setBlock(pos, LightSourceBlocks.withLit(state, false), Block.UPDATE_ALL);
				store.put(pos, new Record(record.sourceUuid(), record.expectedBlockId(), 0,
						record.revision() + 1L, record.managedByPost()));
				WhiteFog.LOGGER.info("WHITEFOG_LIGHT_STATE_CHANGE pos={} remaining=0 lit=false revision={}", pos,
						record.revision() + 1L);
			} else {
				store.put(pos, new Record(record.sourceUuid(), record.expectedBlockId(), remaining,
						record.revision(), record.managedByPost()));
			}
		}
	}

	// ------------------------------------------------------------------
	// Установка предмета
	// ------------------------------------------------------------------

	/**
	 * Пост-обработка {@code getStateForPlacement}: выставляет lit по компоненту и запоминает установку
	 * для commit. Возвращает {@code null} для «заряженный count&gt;1» (отклонение без split/расхода).
	 */
	public static BlockState adjustPlacement(BlockPlaceContext context, BlockState state) {
		if (state == null) {
			return null;
		}
		SourceKind kind = LightSourceBlocks.kind(state);
		if (kind == null) {
			return state;
		}
		ItemStack stack = context.getItemInHand();
		LightFuelComponent.LightFuel itemFuel = LightFuelComponent.read(stack);
		int raw = itemFuel.remainingTicks();
		if (LightFuelComponent.isInvalidChargedStack(stack, raw)) {
			PENDING_PLACEMENT.remove();
			return null;
		}
		int capacity = LightSourceBlocks.capacity(kind);
		int remaining = LightFuelPolicy.normalizeComponentValue(raw, capacity);
		// Блок наследует lit ПРЕДМЕТА: погашенный факел с запасом ставится погашенным.
		boolean lit = remaining > 0 && itemFuel.lit();
		if (!context.getLevel().isClientSide()) {
			// BlockPlaceContext#getClickedPos() в 26.2 возвращает ИМЕННО позицию установки
			// (relativePos = hitPos.relative(face) либо сам hitPos при замене), т.е. позицию
			// будущего блока, а не опорный/нажатый support-блок. Фактический block id всё равно
			// уточняется в commitPlacement по состоянию мира.
			BlockPos placePos = context.getClickedPos().immutable();
			PENDING_PLACEMENT.set(new Pending(context.getLevel().dimension(), placePos, kind,
					LightSourceBlocks.blockId(state), remaining));
		}
		return LightFuelRoundTrip.applyItem(state, stack);
	}

	/** Сбрасывает зафиксированную установку (вызывается на HEAD {@code BlockItem.place}). */
	public static void clearPendingPlacement() {
		PENDING_PLACEMENT.remove();
	}

	/**
	 * Commit установки: создаёт запись источника сразу (без генерационного бонуса).
	 *
	 * <p><b>Почему позиция/блок берутся из мира, а не из контекста.</b> Для
	 * {@code StandingAndWallBlockItem} (факел/факел душ) {@code getStateForPlacement}
	 * вызывается несколько раз (сначала стенным, затем стоячим состоянием), и последним в
	 * {@link Pending} мог оказаться НЕ тот вариант блока, который реально поставлен. Если
	 * записать {@code expectedBlockId} «на глаз», то {@code tickLevel} посчитал бы запись
	 * устаревшей и удалил её — источник исчезал бы из {@code nearest}/панели. Поэтому
	 * фактический id всегда читается из состояния блока по позиции установки
	 * ({@link BlockPlaceContext#getClickedPos()} в 26.2 — это именно позиция реально
	 * поставленного блока, а не опорный/нажатый блок).</p>
	 */
	public static void commitPlacement(Level level, InteractionResult result) {
		Pending pending = PENDING_PLACEMENT.get();
		PENDING_PLACEMENT.remove();
		if (pending == null || level.isClientSide() || result == null || !result.consumesAction()) {
			return;
		}
		if (!(level instanceof ServerLevel serverLevel)) {
			return;
		}
		PlacementTarget target = resolvePlacementTarget(serverLevel, pending);
		if (target == null) {
			return;
		}
		LightSourceStore store = store(serverLevel);
		store.put(target.pos(), new Record(UUID.randomUUID(), target.blockId(), pending.remaining(), 1L, false));
		WhiteFog.LOGGER.info("WHITEFOG_LIGHT_PLACE pos={} block={} remaining={} lit={}", target.pos(),
				target.blockId(), pending.remaining(), LightSourceBlocks.isLit(serverLevel.getBlockState(target.pos())));
	}

	/**
	 * Разрешает фактическую позицию и точный block id поставленного источника.
	 *
	 * <p>Сначала проверяется {@code pending.pos()} — позиция установки из
	 * {@code BlockPlaceContext}. Если там управляемый источник, запись создаётся по его
	 * настоящему состоянию (это устраняет расхождение torch/wall_torch). Если блок там не
	 * управляемый (на случай иной трактовки позиции), выполняется узкая проверка 6 соседних
	 * клеток на управляемый источник ТОГО ЖЕ вида — запись ставится по реальному блоку.</p>
	 */
	private static PlacementTarget resolvePlacementTarget(ServerLevel level, Pending pending) {
		BlockState placedHere = level.getBlockState(pending.pos());
		if (LightSourceBlocks.isManaged(placedHere)) {
			return new PlacementTarget(pending.pos(), LightSourceBlocks.blockId(placedHere));
		}
		for (Direction direction : Direction.values()) {
			BlockPos candidate = pending.pos().relative(direction);
			BlockState state = level.getBlockState(candidate);
			if (LightSourceBlocks.isManaged(state) && LightSourceBlocks.kind(state) == pending.kind()) {
				return new PlacementTarget(candidate.immutable(), LightSourceBlocks.blockId(state));
			}
		}
		return null;
	}

	// ------------------------------------------------------------------
	// Drop / снятие
	// ------------------------------------------------------------------

	/**
	 * Штатный path создаёт item: дописываем {@code remaining} И {@code lit} в ЕДИНСТВЕННЫЙ уже
	 * создаваемый стек и удаляем запись. {@code popResource} вызывается ДО удаления блока
	 * (javap 26.2 {@code Level.destroyBlock}: {@code dropResources} -> {@code setBlock}), поэтому
	 * lit читается из фактического blockstate источника. Дополнительный drop никогда не генерируется.
	 */
	public static void onVanillaDrop(ServerLevel level, BlockPos pos, ItemStack stack) {
		if (stack == null || stack.isEmpty() || stack.getCount() != 1) {
			return;
		}
		LightSourceStore store = store(level);
		Record record = store.get(pos);
		if (record == null) {
			return;
		}
		DropState captured = PENDING_DROP.get();
		boolean contextMatches = captured != null && captured.level() == level && captured.pos().equals(pos);
		BlockState state = resolveDropState(level, pos, contextMatches ? captured.state() : level.getBlockState(pos));
		if (!decorateDrop(store, pos, state, stack)) return;
		boolean lit = LightFuelComponent.readLit(stack);
		WhiteFog.LOGGER.info("WHITEFOG_LIGHT_DROP pos={} remaining={} lit={} recordRevision={}", pos,
				record.remainingTicks(), lit, record.revision());
	}

	/** Production drop commit, also exercised with an isolated store by the runtime regression. */
	static boolean decorateDrop(LightSourceStore store, BlockPos pos, BlockState state, ItemStack stack) {
		Record record = store.get(pos);
		SourceKind kind = LightSourceBlocks.kind(state);
		if (record == null || kind == null || !LightSourceBlocks.matchesExpected(state, record.expectedBlockId())
				|| !LightFuelRoundTrip.writeDrop(stack, state, record.remainingTicks(), kind)) return false;
		store.remove(pos);
		return true;
	}

	private static boolean itemMatchesKind(ItemStack stack, SourceKind kind) {
		if (!(stack.getItem() instanceof net.minecraft.world.item.BlockItem blockItem)) {
			return false;
		}
		return LightSourceBlocks.kind(blockItem.getBlock().defaultBlockState()) == kind;
	}

	/** Captures the pre-removal state for the custom BreakTimerService drop loop. */
	public static DropContext pushDropContext(ServerLevel level, BlockPos pos, BlockState state) {
		SourceKind kind = LightSourceBlocks.kind(state);
		DropContext parent = currentContext();
		PENDING_DROP.set(new DropState(level, pos.immutable(), kind, state, parent));
		return parent;
	}

	public static DropContext pushCustomDropContext(ServerLevel level, BlockPos pos, BlockState state) {
		return pushDropContext(level, pos, state);
	}

	private static DropContext currentContext() {
		DropState state = PENDING_DROP.get();
		return state == null ? null : new DropContext(state.level(), state.pos(), state.kind(), state.state(), state.parent());
	}

	static BlockState resolveDropState(ServerLevel level, BlockPos pos, BlockState fallback) {
		DropState context = PENDING_DROP.get();
		return context != null && context.level() == level && context.pos().equals(pos) ? context.state() : fallback;
	}

	/** Restores the nested state in all exits from the vanilla break method. */
	public static void restoreDropContext(DropContext parent) {
		if (parent == null) PENDING_DROP.remove();
		else PENDING_DROP.set(new DropState(parent.level(), parent.pos(), parent.kind(), parent.state(), parent.parent()));
	}


	// ------------------------------------------------------------------
	// nearest / снимки
	// ------------------------------------------------------------------

	/**
	 * Ближайший loaded валидный managed-источник с LOS в радиусе {@code radius} (расстояние от глаз до
	 * центра блока; tie-break по x/y/z). {@code eye} — позиция глаз вызывающей стороны.
	 *
	 * <p><b>lit не фильтруется:</b> погасший источник (в т.ч. только что поставленный пустой,
	 * {@code remaining=0}) должен быть обнаружим в Work Panel, чтобы игрок мог его заправить/зажечь.
	 * Погасший источник при этом НЕ считается светом: эмиссия блока остаётся 0
	 * ({@code BlockStateLightEmissionMixin}), а darkness exposure читает только
	 * {@code Level#getBrightness(LightLayer.BLOCK, …)} и сервис источников не использует.</p>
	 */
	public static Optional<Snapshot> nearest(ServerLevel level, Vec3 eye, double radius) {
		LightSourceStore store = store(level);
		double radiusSq = radius * radius;
		BlockPos bestPos = null;
		Record bestRecord = null;
		double bestDistSq = Double.MAX_VALUE;
		SourceKind bestKind = null;
		for (Map.Entry<BlockPos, Record> entry : store.entries()) {
			BlockPos pos = entry.getKey();
			Record record = entry.getValue();
			if (!level.isLoaded(pos)) {
				continue;
			}
			if (!LightSourceBlocks.isManaged(level.getBlockState(pos))
					|| !LightSourceBlocks.matchesExpected(level.getBlockState(pos), record.expectedBlockId())) {
				continue;
			}
			double distSq = eye.distanceToSqr(Vec3.atCenterOf(pos));
			if (distSq > radiusSq) {
				continue;
			}
			if (!hasLineOfSight(level, eye, pos)) {
				continue;
			}
			boolean better;
			if (distSq < bestDistSq - 1.0E-6D) {
				better = true;
			} else if (Math.abs(distSq - bestDistSq) <= 1.0E-6D) {
				better = bestPos == null || isTieBetter(pos, bestPos);
			} else {
				better = false;
			}
			if (better) {
				bestDistSq = distSq;
				bestPos = pos;
				bestRecord = record;
				bestKind = LightSourceBlocks.kind(level.getBlockState(pos));
			}
		}
		if (bestPos == null || bestRecord == null || bestKind == null) {
			return Optional.empty();
		}
		boolean lit = LightSourceBlocks.isLit(level.getBlockState(bestPos));
		return Optional.of(new Snapshot(bestPos, bestRecord.sourceUuid(), bestRecord.revision(),
				bestRecord.remainingTicks(), lit, bestKind));
	}

	/** Diagnostic search: existing persistent store only, all ray cells read via getChunkNow. */
	public static Optional<Snapshot> diagnosticNearest(ServerLevel level, Vec3 eye, double radius) {
		LightSourceStore existing = KNOWN_STORES.get(level);
		if (existing == null) return Optional.empty();
		Snapshot best = null;
		double bestDistance = radius * radius;
		for (Map.Entry<BlockPos, Record> entry : existing.entries()) {
			BlockPos pos = entry.getKey();
			var chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
			if (chunk == null) continue;
			BlockState state = chunk.getBlockState(pos);
			SourceKind kind = LightSourceBlocks.kind(state);
			if (kind == null || !LightSourceBlocks.matchesExpected(state, entry.getValue().expectedBlockId())) continue;
			double distance = eye.distanceToSqr(Vec3.atCenterOf(pos));
			if (distance > bestDistance) continue;
			boolean visible = net.minecraft.world.level.BlockGetter.traverseBlocks(eye, Vec3.atCenterOf(pos), level,
					(world, cell) -> {
						var loaded = world.getChunkSource().getChunkNow(cell.getX() >> 4, cell.getZ() >> 4);
						if (loaded == null) return Boolean.FALSE;
						if (cell.equals(pos)) return Boolean.TRUE;
						return loaded.getBlockState(cell).isAir() ? null : Boolean.FALSE;
					}, world -> Boolean.TRUE);
			if (!visible) continue;
			Record r = entry.getValue();
			best = new Snapshot(pos, r.sourceUuid(), r.revision(), r.remainingTicks(), LightSourceBlocks.isLit(state), kind);
			bestDistance = distance;
		}
		return Optional.ofNullable(best);
	}

	private static boolean isTieBetter(BlockPos candidate, BlockPos best) {
		if (candidate.getX() != best.getX()) {
			return candidate.getX() < best.getX();
		}
		if (candidate.getY() != best.getY()) {
			return candidate.getY() < best.getY();
		}
		return candidate.getZ() < best.getZ();
	}

	private static boolean hasLineOfSight(Level level, Vec3 eye, BlockPos pos) {
		BlockHitResult hit = level.clip(new ClipContext(eye, Vec3.atCenterOf(pos), ClipContext.Block.COLLIDER,
				ClipContext.Fluid.NONE, CollisionContext.empty()));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	/** Отправляет снимок ближайшего источника (при изменении; не чаще интервала). */
	private static void sendSnapshots(MinecraftServer server) {
		if (server.getTickCount() % LightConfig.SNAPSHOT_INTERVAL_TICKS != 0) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			try {
				sendSnapshotFor(player);
			} catch (RuntimeException e) {
				WhiteFog.LOGGER.error("White Fog: light snapshot failed for player {}", player.getStringUUID(), e);
			}
		}
	}

	private static void sendSnapshotFor(ServerPlayer player) {
		ServerLevel level = (ServerLevel) player.level();
		Identifier dimension = level.dimension().identifier();
		LightSourceSnapshotPayload payload;
		Optional<Snapshot> nearest = nearest(level, player.getEyePosition(), LightConfig.NEAREST_RADIUS);
		if (nearest.isPresent()) {
			Snapshot s = nearest.get();
			payload = new LightSourceSnapshotPayload(true, dimension, s.pos(), s.sourceUuid(), s.revision(),
					s.remaining(), s.lit(), s.kind().ordinal());
		} else {
			payload = LightSourceSnapshotPayload.absent(dimension);
		}
		LightSourceSnapshotPayload previous = LAST_SNAPSHOT.get(player.getUUID());
		boolean changed = previous == null
				|| previous.present() != payload.present()
				|| !previous.pos().equals(payload.pos())
				|| !previous.sourceUuid().equals(payload.sourceUuid())
				|| previous.revision() != payload.revision()
				|| previous.remaining() != payload.remaining()
				|| previous.lit() != payload.lit();
		if (!changed) {
			return;
		}
		LAST_SNAPSHOT.put(player.getUUID(), payload);
		if (ServerPlayNetworking.canSend(player, LightSourceSnapshotPayload.TYPE)) {
			ServerPlayNetworking.send(player, payload);
		}
	}

	// ------------------------------------------------------------------
	// Refuel / light job
	// ------------------------------------------------------------------

	/** Обрабатывает C2S-запрос {@link LightRefuelPayload} (серверная валидация). */
	public static void handleRefuel(ServerPlayer player, LightRefuelPayload payload) {
		try {
			UUID playerId = player.getUUID();

			// Дедупликация requestSequence в пределах соединения: повторный sequence не начинает job.
			long last = LAST_SEQUENCE.getOrDefault(playerId, Long.MIN_VALUE);
			if (payload.requestSequence() <= last) {
				return;
			}
			LAST_SEQUENCE.put(playerId, (long) payload.requestSequence());

			if (JOBS.containsKey(playerId)) {
				return; // одна runtime job на UUID
			}

			MinecraftServer server = player.level().getServer();
			if (server == null) {
				return;
			}
			ResourceKey<Level> dimensionKey = ResourceKey.create(Registries.DIMENSION, payload.dimension());
			if (!player.level().dimension().equals(dimensionKey)) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_REFUSED, 0, 0);
				return;
			}
			ServerLevel level = (ServerLevel) player.level();
			BlockPos pos = payload.pos();
			if (!level.isLoaded(pos)) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_REFUSED, 0, 0);
				return;
			}
			if (!hasRefuelRights(player, level, pos)) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_REFUSED, 0, 0);
				return;
			}
			LightSourceStore store = ensureChunkInitialized(level, pos);
			Record record = store.get(pos);
			BlockState state = level.getBlockState(pos);
			if (record == null || !LightSourceBlocks.isManaged(state)
					|| !LightSourceBlocks.matchesExpected(state, record.expectedBlockId())) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_STALE, 0, 0);
				return;
			}
			if (payload.sourceUuid() == null || !record.sourceUuid().equals(payload.sourceUuid())
					|| payload.expectedRevision() != record.revision()) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_STALE, record.revision(),
						record.remainingTicks());
				return;
			}
			if (record.managedByPost()) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_MANAGED_BY_POST, record.revision(),
						record.remainingTicks());
				return;
			}

			SourceKind kind = LightSourceBlocks.kind(state);
			if (kind == null) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_STALE, record.revision(),
						record.remainingTicks());
				return;
			}

			ItemStack mainHand = player.getMainHandItem();
			LightFuelPolicy.Fuel fuel = fuelFor(mainHand);
			int addition = fuel == null ? -1 : LightFuelPolicy.additionTicks(kind, fuel);

			if (addition <= 0) {
				// Заправка без подходящего топлива — отказ. Refuel НЕ зажигает источник
				// (единственное действие unlit -> lit — «Зажечь» в меню).
				sendResult(player, payload, LightRefuelResultPayload.STATUS_NO_FUEL, record.revision(),
						record.remainingTicks());
				return;
			}
			if (LightFuelComponent.isInvalidChargedStack(mainHand, LightFuelComponent.readRemaining(mainHand))) {
				sendResult(player, payload, LightRefuelResultPayload.STATUS_REFUSED, record.revision(),
						record.remainingTicks());
				return;
			}
			JOBS.put(playerId, new RefuelJob(dimensionKey, pos.immutable(), record.sourceUuid(),
					mainHand.copy(), fuel, addition, server.getTickCount()));
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: light refuel request failed for player {}", player.getStringUUID(), e);
		}
	}

	private static void tickJobs(MinecraftServer server) {
		if (JOBS.isEmpty()) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			RefuelJob job = JOBS.get(player.getUUID());
			if (job == null) {
				continue;
			}
			try {
				if (!validateJob(server, player, job)) {
					WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=refuel outcome=CANCELLED reason=validation",
							player.getStringUUID(), job.dimension().identifier(), job.pos());
					JOBS.remove(player.getUUID());
					continue;
				}
				if (server.getTickCount() - job.startTick() + 1 >= LightConfig.REFUEL_JOB_TICKS) {
					completeJob(server, player, job);
					JOBS.remove(player.getUUID());
				}
			} catch (RuntimeException e) {
				JOBS.remove(player.getUUID());
				WhiteFog.LOGGER.error("White Fog: light refuel job failed for player {}", player.getStringUUID(), e);
			}
		}
	}

	/** Повторная проверка каждый тик: права/дистанция/LOS/измерение/жив/стек. Отмена ничего не расходует. */
	private static boolean validateJob(MinecraftServer server, ServerPlayer player, RefuelJob job) {
		if (!player.isAlive() || player.isSpectator()) {
			return false;
		}
		if (!player.level().dimension().equals(job.dimension())) {
			return false;
		}
		ServerLevel level = server.getLevel(job.dimension());
		if (level == null || !level.isLoaded(job.pos())) {
			return false;
		}
		if (!hasRefuelRights(player, level, job.pos())) {
			return false;
		}
		BlockState state = level.getBlockState(job.pos());
		LightSourceStore store = store(level);
		Record record = store.get(job.pos());
		if (record == null || !LightSourceBlocks.isManaged(state)
				|| !LightSourceBlocks.matchesExpected(state, record.expectedBlockId())
				|| !record.sourceUuid().equals(job.sourceUuid())) {
			return false; // удаление/замена блока с другим UUID
		}
		ItemStack mainHand = player.getMainHandItem();
		if (!ItemStack.isSameItemSameComponents(mainHand, job.fuelTemplate())
				|| mainHand.getCount() != job.fuelTemplate().getCount()) {
			return false; // смена stack/count/слота
		}
		return true;
	}
	/**
	 * Завершение refuel: перепроверка {@code remaining + addition <= capacity}, атомарное списание,
	 * инкремент ревизии. {@code lit} источника НЕ меняется: заправка никогда не зажигает
	 * (единственное {@code unlit -> lit} — отдельное действие «Зажечь»).
	 */
	private static void completeJob(MinecraftServer server, ServerPlayer player, RefuelJob job) {
		ServerLevel level = server.getLevel(job.dimension());
		if (level == null) {
			return;
		}
		LightSourceStore store = store(level);
		Record record = store.get(job.pos());
		BlockState state = level.getBlockState(job.pos());
		if (record == null || !LightSourceBlocks.isManaged(state)
				|| !record.sourceUuid().equals(job.sourceUuid())) {
			WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=refuel outcome=CANCELLED reason=stale",
					player.getStringUUID(), job.dimension().identifier(), job.pos());
			return;
		}
		SourceKind kind = LightSourceBlocks.kind(state);
		if (kind == null) {
			return;
		}

		int capacity = LightSourceBlocks.capacity(kind);
		int remaining = record.remainingTicks();
		if (!LightFuelPolicy.fits(remaining, job.addition(), capacity)) {
			WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=refuel outcome=REFUSED reason=full",
					player.getStringUUID(), job.dimension().identifier(), job.pos());
			long now = server.getTickCount();
			long lastMessage = FULL_MESSAGE_COOLDOWN.getOrDefault(player.getUUID(), Long.MIN_VALUE);
			if (now - lastMessage >= LightConfig.MESSAGE_COOLDOWN_TICKS) {
				FULL_MESSAGE_COOLDOWN.put(player.getUUID(), now);
				player.sendSystemMessage(Component.literal(LightConfig.MESSAGE_TANK_FULL), true);
			}
			sendResult(player, jobToPayload(job), LightRefuelResultPayload.STATUS_FULL, record.revision(), remaining);
			return;
		}

		// Атомарное списание ровно одного предмета (стек уже проверен на идентичность).
		ItemStack mainHand = player.getMainHandItem();
		mainHand.shrink(1);

		int newRemaining = remaining + job.addition();
		long newRevision = record.revision() + 1L;
		// lit не трогаем: источник остаётся таким, каким был (заправка не зажигает).
		store.put(job.pos(), new Record(record.sourceUuid(), record.expectedBlockId(), newRemaining, newRevision,
				record.managedByPost()));
		WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=refuel outcome=COMPLETE remaining={} lit={}",
				player.getStringUUID(), job.dimension().identifier(), job.pos(), newRemaining, LightSourceBlocks.isLit(state));
		sendResult(player, jobToPayload(job), LightRefuelResultPayload.STATUS_OK, newRevision, newRemaining);
	}

	private static LightRefuelPayload jobToPayload(RefuelJob job) {
		return new LightRefuelPayload(job.dimension().identifier(), job.pos(), job.sourceUuid(), 0L,
				Integer.MIN_VALUE);
	}

	private static void sendResult(ServerPlayer player, LightRefuelPayload request, int status, long revision,
			int remaining) {
		if (!ServerPlayNetworking.canSend(player, LightRefuelResultPayload.TYPE)) {
			return;
		}
		ServerPlayNetworking.send(player, new LightRefuelResultPayload(request.dimension(), request.pos(),
				revision, remaining, status));
	}

	private static LightFuelPolicy.Fuel fuelFor(ItemStack stack) {
		if (stack == null || stack.isEmpty()) {
			return null;
		}
		Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
		if (id == null) {
			return null;
		}
		String path = id.getPath();
		return switch (path) {
			case "coal" -> LightFuelPolicy.Fuel.COAL;
			case "charcoal" -> LightFuelPolicy.Fuel.CHARCOAL;
			case "stick" -> LightFuelPolicy.Fuel.STICK;
			case "oak_log" -> LightFuelPolicy.Fuel.OAK_LOG;
			case "spruce_log" -> LightFuelPolicy.Fuel.SPRUCE_LOG;
			case "birch_log" -> LightFuelPolicy.Fuel.BIRCH_LOG;
			default -> null;
		};
	}

	private static boolean hasRefuelRights(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (player.isSpectator() || !level.mayInteract(player, pos)) {
			return false;
		}
		double maxSq = LightConfig.REFUEL_MAX_DISTANCE * LightConfig.REFUEL_MAX_DISTANCE;
		if (player.getEyePosition().distanceToSqr(Vec3.atCenterOf(pos)) > maxSq) {
			return false;
		}
		return hasLineOfSight(level, player.getEyePosition(), pos);
	}

	// ------------------------------------------------------------------
	// Зажигание костра (vanilla flint & steel / fire charge)
	// ------------------------------------------------------------------

	/**
	 * Отказ в ванильном зажигании костра без топлива (remaining &le; 0). При remaining &gt; 0 ванильный
	 * ignition разрешён — он только включает LIT и не добавляет ticks.
	 */
	public static boolean shouldRefuseCampfireIgnition(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		if (LightSourceBlocks.kind(state) != SourceKind.CAMPFIRE) {
			return false;
		}
		if (LightSourceBlocks.isLit(state)) {
			return false;
		}
		LightSourceStore store = ensureChunkInitialized(level, pos);
		Record record = store.get(pos);
		return record == null || record.remainingTicks() <= 0;
	}

	// ------------------------------------------------------------------
	// Панель/экран источника (ПКМ) — серверно-авторитетные действия (этап поверх 1.6)
	// ------------------------------------------------------------------

	/** Полное состояние источника или {@code null}. Inspect разрешён и для managedByPost. */
	public static PanelInfo panelInfo(ServerLevel level, BlockPos pos) {
		LightSourceStore store = ensureChunkInitialized(level, pos);
		Record record = store.get(pos);
		BlockState state = level.getBlockState(pos);
		if (record == null || !LightSourceBlocks.isManaged(state)
				|| !LightSourceBlocks.matchesExpected(state, record.expectedBlockId())) {
			return null;
		}
		SourceKind kind = LightSourceBlocks.kind(state);
		if (kind == null) {
			return null;
		}
		return new PanelInfo(true, pos.immutable(), record.sourceUuid(), record.revision(), record.remainingTicks(),
				LightSourceBlocks.isLit(state), kind, record.managedByPost(), LightSourceBlocks.capacity(kind));
	}

	/**
	 * Открывает серверное меню источника. Валидирует права/дистанцию/LOS; при отказе меню не
	 * открывается (ванильное поведение/ничего). Клиент автоматически открывает
	 * {@code LightSourceScreen} по серверному {@code openMenu}.
	 */
	public static boolean openSourceMenu(ServerPlayer player, ServerLevel level, BlockPos pos) {
		try {
			if (player.isSpectator() || !hasMenuRights(player, level, pos)) {
				return false;
			}
			PanelInfo info = panelInfo(level, pos);
			if (info == null) {
				return false;
			}
			player.openMenu(new SimpleMenuProvider(
					(syncId, inventory, p) -> new LightSourceMenu(syncId, inventory, pos.immutable(),
							info.sourceUuid(), p),
					Component.literal("Источник света")));
			OPEN_PANEL_SIG.put(player.getUUID(), signature(info));
			OPEN_PANEL_LAST_SEND.put(player.getUUID(),
					level.getServer() == null ? 0L : level.getServer().getTickCount());
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_OPEN_OK);
			return true;
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: open light source menu failed for player {}",
					player.getStringUUID(), e);
			return false;
		}
	}

	/** Кнопка меню источника: Потушить (2) / Зажечь (3) / Заправить (1). */
	public static void handleMenuButton(ServerPlayer player, ServerLevel level, BlockPos pos, UUID expectedUuid,
			int action) {
		try {
			if (!hasMenuRights(player, level, pos)) {
				sendPanel(player, level, pos, SourceActionPolicy.STATUS_REFUSED);
				clearPanelState(player);
				player.closeContainer();
				return;
			}
			PanelInfo info = panelInfo(level, pos);
			if (info == null || expectedUuid == null || !info.sourceUuid().equals(expectedUuid)) {
				sendPanel(player, level, pos, SourceActionPolicy.STATUS_STALE);
				clearPanelState(player);
				player.closeContainer();
				return;
			}
			switch (action) {
				case SourceActionPolicy.ACTION_EXTINGUISH -> doExtinguish(player, level, pos, info);
				case SourceActionPolicy.ACTION_RELIGHT -> doRelight(player, level, pos, info);
				case SourceActionPolicy.ACTION_REFUEL -> doRefuelFromMenu(player, level, pos, info);
				default -> sendPanel(player, level, pos, SourceActionPolicy.STATUS_REFUSED);
			}
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: light source menu action failed for player {}",
					player.getStringUUID(), e);
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_REFUSED);
		}
	}

	/** Тушение: мгновенно, сохраняет остаток, топливо не тратит. */
	private static void doExtinguish(ServerPlayer player, ServerLevel level, BlockPos pos, PanelInfo info) {
		if (info.managedByPost()) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_MANAGED_BY_POST);
			return;
		}
		if (!info.lit()) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_ALREADY_UNLIT);
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!LightSourceBlocks.isManaged(state)) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_STALE);
			return;
		}
		level.setBlock(pos, LightSourceBlocks.withLit(state, false), Block.UPDATE_ALL);
		bumpRevision(level, pos);
		WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=extinguish outcome=SUCCESS remaining={}",
				player.getStringUUID(), level.dimension().identifier(), pos, info.remaining());
		sendPanel(player, level, pos, SourceActionPolicy.STATUS_OK);
	}

	/** Зажигание: мгновенно, требует остаток &gt; 0, топливо не тратит. */
	private static void doRelight(ServerPlayer player, ServerLevel level, BlockPos pos, PanelInfo info) {
		if (info.managedByPost()) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_MANAGED_BY_POST);
			return;
		}
		if (info.lit()) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_ALREADY_LIT);
			return;
		}
		if (!SourceActionPolicy.canRelight(info.lit(), info.remaining(), info.managedByPost())) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_NO_FUEL_TO_LIGHT);
			return;
		}
		BlockState state = level.getBlockState(pos);
		if (!LightSourceBlocks.isManaged(state)) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_STALE);
			return;
		}
		level.setBlock(pos, LightSourceBlocks.withLit(state, true), Block.UPDATE_ALL);
		bumpRevision(level, pos);
		WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=relight outcome=SUCCESS remaining={}",
				player.getStringUUID(), level.dimension().identifier(), pos, info.remaining());
		sendPanel(player, level, pos, SourceActionPolicy.STATUS_OK);
	}

	/**
	 * Заправка из меню: та же модель, что и G-панель — 20-тиковый refuel-job. НИКОГДА не меняет
	 * {@code lit}: погашенный источник остаётся погашенным (зажигает только отдельное «Зажечь»).
	 * Переполнение — точный текст {@link LightConfig#MESSAGE_TANK_FULL}, без частичного списания.
	 */
	private static void doRefuelFromMenu(ServerPlayer player, ServerLevel level, BlockPos pos, PanelInfo info) {
		if (info.managedByPost()) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_MANAGED_BY_POST);
			return;
		}
		UUID playerId = player.getUUID();
		ItemStack mainHand = player.getMainHandItem();
		LightFuelPolicy.Fuel fuel = fuelFor(mainHand);
		int addition = fuel == null ? -1 : LightFuelPolicy.additionTicks(info.kind(), fuel);

		if (addition <= 0) {
			// Заправка без подходящего топлива — отказ, а НЕ авто-зажигание.
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_NO_FUEL);
			WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=refuel outcome=REFUSED reason=no_fuel",
					player.getStringUUID(), level.dimension().identifier(), pos);
			return;
		}
		if (JOBS.containsKey(playerId)) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_ACCEPTED);
			return;
		}
		if (LightFuelComponent.isInvalidChargedStack(mainHand, LightFuelComponent.readRemaining(mainHand))) {
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_REFUSED);
			return;
		}
		if (!SourceActionPolicy.canRefuel(info.remaining(), addition, info.capacity())) {
			sendTankFull(player);
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_FULL);
			return;
		}
		long now = player.level().getServer() == null ? 0L : player.level().getServer().getTickCount();
		JOBS.put(playerId, new RefuelJob(level.dimension(), pos.immutable(), info.sourceUuid(),
				mainHand.copy(), fuel, addition, now));
		WhiteFog.LOGGER.info("WHITEFOG_LIGHT_ACTION player={} dimension={} pos={} action=refuel outcome=STARTED addition={}",
				player.getStringUUID(), level.dimension().identifier(), pos, addition);
		sendPanel(player, level, pos, SourceActionPolicy.STATUS_ACCEPTED);
	}

	/**
	 * Периодическое обновление открытой панели (вызывается из {@code LightSourceMenu#broadcastChanges}).
	 * Шлёт S2C только при изменении ревизии/остатка/lit; при исчезновении источника — STALE и закрытие.
	 */
	public static void tickOpenMenu(ServerPlayer player, ServerLevel level, BlockPos pos, UUID expectedUuid) {
		try {
			if (!player.isAlive() || player.level() != level) {
				clearPanelState(player);
				return;
			}
			PanelInfo info = panelInfo(level, pos);
			if (info == null || expectedUuid == null || !info.sourceUuid().equals(expectedUuid)) {
				clearPanelState(player);
				sendPanel(player, level, pos, SourceActionPolicy.STATUS_STALE);
				player.closeContainer();
				return;
			}
			PanelSignature sig = signature(info);
			PanelSignature previous = OPEN_PANEL_SIG.get(player.getUUID());
			if (previous == null || previous.equals(sig)) {
				return;
			}
			long now = level.getServer() == null ? 0L : level.getServer().getTickCount();
			// Ревизия/lit меняются мгновенно (тушение/зажигание/refuel); убывание remaining —
			// не чаще интервала снимка, чтобы не слать пакет каждый тик.
			boolean revisionChanged = previous.revision() != sig.revision() || previous.lit() != sig.lit();
			Long last = OPEN_PANEL_LAST_SEND.get(player.getUUID());
			boolean intervalElapsed = last == null || now - last >= LightConfig.SNAPSHOT_INTERVAL_TICKS;
			if (!revisionChanged && !intervalElapsed) {
				return;
			}
			OPEN_PANEL_SIG.put(player.getUUID(), sig);
			OPEN_PANEL_LAST_SEND.put(player.getUUID(), now);
			sendPanel(player, level, pos, SourceActionPolicy.STATUS_OK);
		} catch (RuntimeException e) {
			WhiteFog.LOGGER.error("White Fog: light source panel refresh failed for player {}",
					player.getStringUUID(), e);
		}
	}

	private static PanelSignature signature(PanelInfo info) {
		return new PanelSignature(info.pos().immutable(), info.revision(), info.remaining(), info.lit());
	}

	/** Сбрасывает runtime-состояние открытой панели игрока. */
	private static void clearPanelState(ServerPlayer player) {
		UUID id = player.getUUID();
		OPEN_PANEL_SIG.remove(id);
		OPEN_PANEL_LAST_SEND.remove(id);
	}

	/** Инкремент ревизии записи без изменения остатка/владельца. */
	private static void bumpRevision(ServerLevel level, BlockPos pos) {
		LightSourceStore store = store(level);
		Record record = store.get(pos);
		if (record != null) {
			store.put(pos, new Record(record.sourceUuid(), record.expectedBlockId(), record.remainingTicks(),
					record.revision() + 1L, record.managedByPost()));
		}
	}

	private static void sendTankFull(ServerPlayer player) {
		long now = player.level().getServer() == null ? 0L : player.level().getServer().getTickCount();
		long lastMessage = FULL_MESSAGE_COOLDOWN.getOrDefault(player.getUUID(), Long.MIN_VALUE);
		if (now - lastMessage >= LightConfig.MESSAGE_COOLDOWN_TICKS) {
			FULL_MESSAGE_COOLDOWN.put(player.getUUID(), now);
			player.sendSystemMessage(Component.literal(LightConfig.MESSAGE_TANK_FULL), true);
		}
	}

	/** Отправляет S2C-состояние панели источника (или absent). */
	private static void sendPanel(ServerPlayer player, ServerLevel level, BlockPos pos, int status) {
		if (!ServerPlayNetworking.canSend(player, SourcePanelPayload.TYPE)) {
			return;
		}
		PanelInfo info = panelInfo(level, pos);
		SourcePanelPayload payload;
		if (info == null) {
			payload = SourcePanelPayload.absent(level.dimension().identifier(), pos, status);
		} else {
			payload = new SourcePanelPayload(true, level.dimension().identifier(), info.pos(), info.sourceUuid(),
					info.revision(), info.remaining(), info.lit(), info.kind().ordinal(), info.capacity(),
					info.managedByPost(), status);
		}
		ServerPlayNetworking.send(player, payload);
	}

	/** Права на открытие/действия панели: не спектатор, mayInteract, ванильная дистанция, LOS. */
	private static boolean hasMenuRights(ServerPlayer player, ServerLevel level, BlockPos pos) {
		if (player.isSpectator() || !level.mayInteract(player, pos)) {
			return false;
		}
		if (!player.isWithinBlockInteractionRange(pos, 1.0D)) {
			return false;
		}
		return hasLineOfSight(level, player.getEyePosition(), pos);
	}

	// ------------------------------------------------------------------
	// Служебное
	// ------------------------------------------------------------------

	/** Удаляет runtime-состояние игрока при disconnect (job/sequence/cooldown/снимок/панель). */
	public static void clear(ServerPlayer player) {
		UUID id = player.getUUID();
		JOBS.remove(id);
		LAST_SEQUENCE.remove(id);
		FULL_MESSAGE_COOLDOWN.remove(id);
		LAST_SNAPSHOT.remove(id);
		OPEN_PANEL_SIG.remove(id);
		OPEN_PANEL_LAST_SEND.remove(id);
	}

	/** Диагностика (dev): строки store измерения. */
	public static List<String> describe(ServerLevel level) {
		return store(level).describe();
	}
}
