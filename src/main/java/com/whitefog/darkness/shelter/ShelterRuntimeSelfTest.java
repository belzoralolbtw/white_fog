package com.whitefog.darkness.shelter;

import com.whitefog.WhiteFog;
import com.whitefog.darkness.shelter.Voxels.Cell;
import com.whitefog.darkness.shelter.Voxels.Face;
import com.whitefog.darkness.shelter.Voxels.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.material.FluidState;
import java.util.HashMap;
import java.util.Map;

/** Dev-only real 26.2 shape/adapter fixtures. Not proof of player rooms or live chunk lifecycle. */
public final class ShelterRuntimeSelfTest {
    private static boolean ran;
    private static int assertions;
    private ShelterRuntimeSelfTest() { }

    public static void run() {
        if (ran) { return; }
        ran = true;
        long started = System.nanoTime();
        try {
            Fixture fixture = new Fixture();
            BlockPos origin = new BlockPos(0, 1, 0);
            for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                Face face = Face.valueOf(facing.name());
                for (DoubleBlockHalf half : DoubleBlockHalf.values()) {
                    BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, facing)
                            .setValue(DoorBlock.HALF, half).setValue(DoorBlock.OPEN, false);
                    Cell closed = ShelterProvider.classify(door, fixture, origin);
                    check(closed.blockedFaces() == face.opposite().bit(), "door direction/half " + facing + half);
                    check(!closed.fullSolid() && closed.standingSpace(), "closed door not a full cube");
                    Cell open = ShelterProvider.classify(door.setValue(DoorBlock.OPEN, true), fixture, origin);
                    check(open.blockedFaces() == 0 && open.supportFaces() == 0, "open door leaks");
                    Fixture room = room();
                    int x = face.dx != 0 ? (face.dx < 0 ? -1 : 3) : 1;
                    int z = face.dz != 0 ? (face.dz < 0 ? -1 : 3) : 1;
                    room.states.put(new Pos(x, 1, z), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
                    room.states.put(new Pos(x, 2, z), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
                    check(detect(room).valid(), "real closed-door room " + facing + half);
                    room.states.replaceAll((p, s) -> s.getBlock() instanceof DoorBlock ? s.setValue(DoorBlock.OPEN, true) : s);
                    check(!detect(room).valid(), "real open-door room " + facing + half);
                }
            }
            for (Half half : Half.values()) {
                for (Direction facing : new Direction[]{Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST}) {
                    BlockState trap = Blocks.OAK_TRAPDOOR.defaultBlockState().setValue(TrapDoorBlock.HALF, half)
                            .setValue(TrapDoorBlock.FACING, facing).setValue(TrapDoorBlock.OPEN, false);
                    Cell closed = ShelterProvider.classify(trap, fixture, origin);
                    check(closed.blockedFaces() == (half == Half.TOP ? Face.UP : Face.DOWN).bit(), "trapdoor real plane");
                    Cell open = ShelterProvider.classify(trap.setValue(TrapDoorBlock.OPEN, true), fixture, origin);
                    check(open.blockedFaces() == 0 && open.supportFaces() == 0, "open trapdoor has no support");
                    Fixture room = room();
                    Pos endpoint = new Pos(1, half == Half.TOP ? 0 : 3, 1);
                    room.states.put(endpoint, trap);
                    check(detect(room).valid(), "real closed-trapdoor room");
                    room.states.put(endpoint, trap.setValue(TrapDoorBlock.OPEN, true));
                    check(!detect(room).valid(), "real open-trapdoor room");
                }
            }
            check(ShelterProvider.classify(Blocks.STONE.defaultBlockState(), fixture, origin).fullSolid(), "full stone");
            check(ShelterProvider.classify(Blocks.AIR.defaultBlockState(), fixture, origin).clearSpace(), "air clear");
            check(ShelterProvider.classify(Blocks.WATER.defaultBlockState(), fixture, origin).fluid(), "water not support");
            for (SlabType type : SlabType.values()) {
                Cell slab = ShelterProvider.classify(Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, type), fixture, origin);
                check(type == SlabType.DOUBLE ? slab.fullSolid() : slab.blockedFaces() == 0, "real slab " + type);
                if (type == SlabType.BOTTOM) { check(!slab.supports(Face.UP), "bottom slab no upper boundary"); }
                if (type == SlabType.TOP) { check(!slab.supports(Face.DOWN), "top slab no lower boundary"); }
            }
            Cell fence = ShelterProvider.classify(Blocks.OAK_FENCE.defaultBlockState(), fixture, origin);
            check(fence.blockedFaces() == 0 && !fence.supports(Face.UP), "real fence leaks");
            Fixture room = room();
            check(detect(room).valid() && detect(room).interior().size() == 18, "real shape minimum room");
            room.states.put(new Pos(1, 3, 1), Blocks.AIR.defaultBlockState());
            check(!detect(room).valid(), "real roof hole");
            boolean handler = java.util.Arrays.stream(LevelChunk.class.getDeclaredMethods())
                    .anyMatch(m -> m.getName().contains("whitefog$shelterChanged"));
            check(handler, "LevelChunk mutation handler applied");
            WhiteFog.LOGGER.info("WHITEFOG_SHELTER_SELFTEST assertions={} handlers={} elapsed_ms={} status=SUCCESS",
                    assertions, handler, (System.nanoTime() - started) / 1_000_000);
        } catch (RuntimeException | AssertionError error) {
            WhiteFog.LOGGER.error("WHITEFOG_SHELTER_SELFTEST assertions={} status=FAILURE", assertions, error);
        }
    }

    private static void check(boolean valid, String message) {
        assertions++;
        if (!valid) { throw new AssertionError(message); }
    }
    private static ShelterSnapshot detect(Fixture fixture) {
        return new ShelterDetector((d, p, e) -> { throw e; }).detect("fixture", new Pos(1, 1, 1), fixture);
    }
    private static Fixture room() {
        Fixture f = new Fixture();
        for (int x = -1; x <= 3; x++) for (int y = 0; y <= 3; y++) for (int z = -1; z <= 3; z++) {
            boolean wall = x == -1 || x == 3 || y == 0 || y == 3 || z == -1 || z == 3;
            f.states.put(new Pos(x, y, z), (wall ? Blocks.STONE : Blocks.AIR).defaultBlockState());
        }
        return f;
    }
    private static final class Fixture implements BlockGetter, Voxels.Provider {
        final Map<Pos, BlockState> states = new HashMap<>();
        @Override public boolean isLoaded(String dimension, Pos p) { return true; }
        @Override public Cell readLoaded(String dimension, Pos p) {
            BlockPos position = new BlockPos(p.x(), p.y(), p.z());
            return ShelterProvider.classify(getBlockState(position), this, position);
        }
        @Override public BlockState getBlockState(BlockPos p) {
            return states.getOrDefault(new Pos(p.getX(), p.getY(), p.getZ()), Blocks.AIR.defaultBlockState());
        }
        @Override public FluidState getFluidState(BlockPos p) { return getBlockState(p).getFluidState(); }
        @Override public BlockEntity getBlockEntity(BlockPos p) { return null; }
        @Override public int getHeight() { return 384; }
        @Override public int getMinY() { return -64; }
    }
}
