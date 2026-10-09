package com.whitefog.darkness.shelter;

import com.whitefog.WhiteFog;
import com.whitefog.darkness.shelter.CollisionMasks.Box;
import com.whitefog.darkness.shelter.CollisionMasks.Kind;
import com.whitefog.darkness.shelter.Voxels.Cell;
import com.whitefog.darkness.shelter.Voxels.Pos;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Server-thread-only loaded view; collision code never receives a Level capable of loading chunks. */
public final class ShelterProvider {
    public record Diagnostic(ShelterSnapshot snapshot, ShelterCache.Entry cacheEntry, long cacheAge,
            boolean cacheStale, boolean cachePresent) { }
    private static final ShelterCache CACHE = new ShelterCache(new ShelterDetector(ShelterProvider::readFailed));
    private static final java.util.Map<UUID, String> LAST_TRUTH = new java.util.HashMap<>();

    private static void readFailed(String dimension, Pos pos, RuntimeException error) {
        if (FabricLoader.getInstance().isDevelopmentEnvironment()) {
            WhiteFog.LOGGER.warn("WHITEFOG_SHELTER_READ_FAILED dimension={} pos={}", dimension, pos, error);
        }
    }

    private ShelterProvider() { }

    public static boolean isSheltered(ServerPlayer player) {
        if (!player.isAlive()) { clear(player.getUUID()); return false; }
        BlockPos feet = player.blockPosition();
        ServerLevel level = player.level();
        String dimension = dimension(level);
        try {
            ShelterSnapshot sampled = CACHE.sample(player.getUUID(), dimension, pos(feet),
                    level.getServer().getTickCount(), new LoadedView(level));
            String truth = dimension + "|" + sampled.valid() + "|" + sampled.reason();
            if (!truth.equals(LAST_TRUTH.put(player.getUUID(), truth))) {
                WhiteFog.LOGGER.info("WHITEFOG_SHELTER_CHANGE player={} dimension={} feet={} valid={} reason={}",
                        player.getStringUUID(), dimension, feet, sampled.valid(), sampled.reason());
            }
            return sampled.valid();
        } catch (RuntimeException error) {
            clear(player.getUUID());
            readFailed(dimension, pos(feet), error);
            return false;
        }
    }

    /**
     * Read-only проверка укрытия в произвольной loaded позиции (кандидат тёмного спавна, этап 1.8).
     * Использует тот же detector без записи в per-player cache и без загрузки чанков. При ошибке
     * чтения возвращает {@code false} (не укрытие) и пишет dev-диагностику — как и сам detector.
     */
    public static boolean isShelteredAt(ServerLevel level, BlockPos feet) {
        try {
            return CACHE.diagnose(dimension(level), pos(feet), new LoadedView(level)).valid();
        } catch (RuntimeException error) {
            readFailed(dimension(level), pos(feet), error);
            return false;
        }
    }

    /** Read-only diagnostic view; performs the same loaded-only sample as exposure. */
    public static Diagnostic diagnostics(ServerPlayer player) {
        if (!player.isAlive()) {
            return null;
        }
        BlockPos feet = player.blockPosition();
        ServerLevel level = player.level();
        String dimension = dimension(level);
        try {
            long tick = level.getServer().getTickCount();
            ShelterCache.Entry entry = CACHE.entry(player.getUUID());
            long age = entry == null ? -1L : tick - entry.checkedAt();
            boolean same = entry != null && entry.dimension().equals(dimension) && entry.feetPos().equals(pos(feet));
            boolean stale = !same || age < 0L || age >= ShelterCache.TTL_TICKS;
            ShelterSnapshot snapshot = CACHE.diagnose(dimension, pos(feet), new LoadedView(level));
            return new Diagnostic(snapshot, same ? entry : null, same ? age : -1L, stale, same && !stale);
        } catch (RuntimeException error) {
            readFailed(dimension, pos(feet), error);
            return new Diagnostic(new ShelterSnapshot(false, pos(feet), pos(feet),
                    ShelterSnapshot.Reason.OPEN_VOLUME, Set.of(), Set.of()), null, -1L, true, false);
        }
    }

    public static void clear(UUID player) { CACHE.remove(player); LAST_TRUTH.remove(player); }
    public static void clearAll() { CACHE.clear(); LAST_TRUTH.clear(); }
    public static void blockChanged(ServerLevel level, BlockPos position) {
        CACHE.blockChanged(dimension(level), pos(position)); // Invalidation only; no recursive detector/world read.
    }
    public static void chunkUnloaded(ServerLevel level, int x, int z) { CACHE.chunkUnloaded(dimension(level), x, z); }
    public static void levelUnloaded(ServerLevel level) { CACHE.dimensionUnloaded(dimension(level)); }
    private static String dimension(ServerLevel level) { return level.dimension().identifier().toString(); }
    private static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }
    private static BlockPos blockPos(Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

    static Cell classify(BlockState state, BlockGetter view, BlockPos position) {
        Kind kind = Kind.ORDINARY;
        if (state.getBlock() instanceof DoorBlock) {
            kind = state.getValue(DoorBlock.OPEN) ? Kind.OPEN_DOOR : Kind.CLOSED_DOOR;
        } else if (state.getBlock() instanceof TrapDoorBlock) {
            kind = state.getValue(TrapDoorBlock.OPEN) ? Kind.OPEN_TRAPDOOR : Kind.CLOSED_TRAPDOOR;
        }
        List<AABB> boxes = state.getCollisionShape(view, position).toAabbs();
        // Centered vanilla-width feet fit; partial shapes still remain permeable during leak search.
        boolean standing = boxes.stream().noneMatch(b -> b.intersects(.2, 0, .2, .8, 1, .8));
        return CollisionMasks.classify(kind, boxes.stream()
                .map(b -> new Box(b.minX, b.minY, b.minZ, b.maxX, b.maxY, b.maxZ)).toList(),
                !state.getFluidState().isEmpty(), standing);
    }

    private static final class LoadedView implements BlockGetter, Voxels.Provider {
        private static final int MAX_SHAPE_READS = 4096;
        private final ServerLevel level;
        private final Set<Pos> dependencies = new LinkedHashSet<>();
        private Pos shapeOrigin;
        LoadedView(ServerLevel level) { this.level = level; }
        private boolean outside(Pos p) {
            return p.y() < getMinY() || p.y() > level.getMaxY()
                    || Math.abs((long) p.x()) >= 30_000_000 || Math.abs((long) p.z()) >= 30_000_000;
        }
        @Override public boolean isLoaded(String dimension, Pos p) {
            boolean loaded = dimension.equals(dimension(level)) && (outside(p)
                    || level.getChunkSource().getChunkNow(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16)) != null);
            if (!loaded && FabricLoader.getInstance().isDevelopmentEnvironment()) {
                WhiteFog.LOGGER.debug("WHITEFOG_SHELTER_UNLOADED dimension={} pos={}", dimension, p);
            }
            return loaded;
        }
        @Override public Cell readLoaded(String dimension, Pos p) {
            if (outside(p)) { return Cell.exterior(); }
            shapeOrigin = p;
            try { return classify(getBlockState(blockPos(p)), this, blockPos(p)); }
            finally { shapeOrigin = null; }
        }
        @Override public Set<Pos> dependencies() { return Set.copyOf(dependencies); }
        private LevelChunk chunk(BlockPos position) {
            Pos p = pos(position);
            if (shapeOrigin != null && (Math.abs((long) p.x() - shapeOrigin.x()) > 1
                    || Math.abs((long) p.y() - shapeOrigin.y()) > 1
                    || Math.abs((long) p.z() - shapeOrigin.z()) > 1)) {
                throw new IllegalStateException("Collision read outside bounded neighbor halo: " + p);
            }
            dependencies.add(p);
            if (dependencies.size() > MAX_SHAPE_READS) { throw new IllegalStateException("Collision read budget: " + p); }
            return outside(p) ? null : level.getChunkSource().getChunkNow(position.getX() >> 4, position.getZ() >> 4);
        }
        @Override public BlockState getBlockState(BlockPos position) {
            LevelChunk chunk = chunk(position);
            // Missing dependency is recorded; detector checks it and returns UNLOADED, never valid.
            return chunk == null ? Blocks.AIR.defaultBlockState() : chunk.getBlockState(position);
        }
        @Override public FluidState getFluidState(BlockPos position) { return getBlockState(position).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos position) {
            LevelChunk chunk = chunk(position);
            // javap 26.2: even CHECK promotes pending NBT. Read the existing map without promotion/removal.
            return chunk == null ? null : chunk.getBlockEntities().get(position);
        }
        @Override public int getHeight() { return level.getHeight(); }
        @Override public int getMinY() { return level.getMinY(); }
    }
}
