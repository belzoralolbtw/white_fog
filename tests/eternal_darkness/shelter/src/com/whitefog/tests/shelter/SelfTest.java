package com.whitefog.tests.shelter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;
import com.whitefog.darkness.LightExposurePolicy;
import com.whitefog.darkness.shelter.CollisionMasks;
import com.whitefog.darkness.shelter.ShelterCache;
import com.whitefog.darkness.shelter.ShelterDetector;
import com.whitefog.darkness.shelter.ShelterSnapshot;
import com.whitefog.darkness.shelter.Voxels;
import com.whitefog.darkness.shelter.CollisionMasks.Box;
import com.whitefog.darkness.shelter.CollisionMasks.Kind;
import com.whitefog.darkness.shelter.ShelterSnapshot.Reason;
import com.whitefog.darkness.shelter.Voxels.Cell;
import com.whitefog.darkness.shelter.Voxels.Face;
import com.whitefog.darkness.shelter.Voxels.Pos;

/** Deterministic stage 1.7 acceptance sandbox. It is not Minecraft collision/runtime proof. */
public final class SelfTest {
    private static int tests;
    private static final String DIM = "minecraft:overworld";
    private static final Pos FEET = new Pos(1, 1, 1);

    public static void main(String[] args) {
        long start = System.nanoTime();
        try {
            basicRoomAndReasons();
            doorsAndMasks();
            sizeAndGeometryLimits();
            boundaryAndFailureClosed();
            deterministicOrderAndShapeMasks();
            columnAndDoorInclusionContract();
            cacheLifecycleAndInvalidation();
            cacheFailClosedAndBoundaries();
            cacheDiagnosticsAreReadOnly();
            shapeNeighborDependencies();
            exposureContract();
            System.out.println("SHELTER_SELFTEST tests=" + tests + " elapsed_ms=" + elapsed(start) + " status=SUCCESS");
        } catch (AssertionError error) {
            System.err.println("SHELTER_SELFTEST tests=" + tests + " elapsed_ms=" + elapsed(start) + " status=FAILURE");
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void basicRoomAndReasons() {
        TestWorld world = room(3, 2, 3);
        ShelterSnapshot found = detect(world, FEET);
        check(found.valid(), "3x2x3 closed shell is valid");
        check(found.interior().size() == 18, "minimum room has 18 cells");
        check(found.bbox().min().equals(new Pos(0, 1, 0)) && found.bbox().max().equals(new Pos(2, 2, 2)), "bbox interior");

        TestWorld water = room(3, 2, 3); water.put(FEET, Cell.water());
        check(detect(water, FEET).reason() == Reason.INVALID_START, "water feet is invalid start");
        TestWorld solid = room(3, 2, 3); solid.put(FEET, Cell.solid());
        check(detect(solid, FEET).reason() == Reason.INVALID_START, "full solid feet is invalid start");

        TestWorld floorHole = room(3, 2, 3); floorHole.put(new Pos(1, 0, 1), Cell.air());
        check(!detect(floorHole, FEET).valid(), "floor hole leaks");
        TestWorld slabGap = room(3, 2, 3);
        slabGap.put(new Pos(1, 0, 1), CollisionMasks.classify(Kind.ORDINARY,
                List.of(new Box(0, 0, 0, 1, .5, 1)), false, true));
        check(!detect(slabGap, FEET).valid(), "partial slab floor is not hermetic");
        TestWorld roofHole = room(3, 2, 3); roofHole.put(new Pos(1, 3, 1), Cell.air());
        check(!detect(roofHole, FEET).valid(), "single roof hole opens volume");
        TestWorld noFloor = room(3, 2, 3); noFloor.put(new Pos(1, 0, 1), new Cell(false, false, true, true, Voxels.ALL, 0, false));
        check(detect(noFloor, FEET).reason() == Reason.NO_FLOOR, "sealed missing floor reports NO_FLOOR");
        TestWorld noRoof = room(3, 2, 3); noRoof.put(new Pos(1, 3, 1), new Cell(false, false, true, true, Voxels.ALL, 0, false));
        check(detect(noRoof, FEET).reason() == Reason.NO_ROOF, "sealed missing roof reports NO_ROOF");
        TestWorld fenceWall = room(3, 2, 3);
        fenceWall.put(new Pos(-1, 1, 1), CollisionMasks.classify(Kind.ORDINARY,
                List.of(new Box(.4, 0, .4, .6, 1, .6)), false, true));
        check(!detect(fenceWall, FEET).valid(), "fence wall leaks to exterior");
        TestWorld slabWall = room(3, 2, 3);
        slabWall.put(new Pos(-1, 1, 1), CollisionMasks.classify(Kind.ORDINARY,
                List.of(new Box(0, 0, 0, 1, .5, 1)), false, true));
        check(!detect(slabWall, FEET).valid(), "slab wall leaks to exterior");
    }

    private static void doorsAndMasks() {
        for (Face facing : List.of(Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST)) {
            for (boolean upper : new boolean[]{false, true}) {
                Cell door = door(facing, false);
                TestWorld closed = roomWithDoor(facing, door, upper);
                check(detect(closed, FEET).valid(), "closed door sealed facing=" + facing + " half=" + upper);
                TestWorld open = roomWithDoor(facing, door(facing, true), upper);
                check(!detect(open, FEET).valid(), "open door leaks facing=" + facing + " half=" + upper);
                check(door.blockedFaces() == facing.opposite().bit(), "collision-derived face is opposite FACING " + facing);
                for (Face direction : Voxels.ORDER) {
                    check(Voxels.blocked(Cell.air(), door, direction)
                            == Voxels.blocked(door, Cell.air(), direction.opposite()), "paired transition symmetry " + facing + " " + direction);
                    check(door.blocks(direction) == (direction == facing.opposite()), "no tangential door barrier " + facing + " " + direction);
                }
            }
        }
        for (boolean top : new boolean[]{false, true}) {
            Cell trapdoor = trapdoor(top, false);
            TestWorld closed = roomWithTrapdoor(trapdoor, top);
            check(detect(closed, FEET).valid(), "closed trapdoor sealed top=" + top);
            TestWorld open = roomWithTrapdoor(trapdoor(top, true), top);
            check(!detect(open, FEET).valid(), "open trapdoor does not seal top=" + top);
            check(trapdoor.blockedFaces() == (top ? Face.UP : Face.DOWN).bit(), "trapdoor actual boundary mask top=" + top);
            check(!trapdoor.blocks(Face.NORTH) && !trapdoor.blocks(Face.EAST), "trapdoor has no horizontal walls");
        }
        TestWorld many = roomWithDoor(Face.NORTH, door(Face.NORTH, false), false);
        TestWorld extra = roomWithDoor(Face.SOUTH, door(Face.SOUTH, false), true);
        many.cells.put(new Pos(1, 2, 3), extra.cells.get(new Pos(1, 2, 3)));
        check(detect(many, FEET).valid(), "multiple closed doors are allowed");
    }

    private static void sizeAndGeometryLimits() {
        TestWorld exact = room(5, 5, 5);
        ShelterSnapshot exactResult = detect(exact, new Pos(2, 1, 2));
        check(exactResult.valid(), "125 cells are accepted inclusively");
        check(exactResult.interior().size() == 125, "125th cell is retained, not rejected");

        TestWorld oneMore = room(6, 3, 7);
        check(detect(oneMore, new Pos(3, 1, 3)).reason() == Reason.TOO_LARGE, "126th cell rejected");

        TestWorld widthNine = room(9, 2, 3);
        ShelterSnapshot widthNineResult = detect(widthNine, new Pos(4, 1, 1));
        check(widthNineResult.valid(), "bbox width 9 allowed");
        TestWorld widthTen = room(10, 2, 3);
        check(detect(widthTen, new Pos(5, 1, 1)).reason() == Reason.TOO_LARGE, "bbox width 10 rejected");
        TestWorld seventeen = room(3, 2, 3);
        seventeen.put(new Pos(2, 2, 2), Cell.solid());
        check(detect(seventeen, new Pos(1, 1, 1)).reason() == Reason.TOO_SMALL, "17 cells rejected");
        check(detect(seventeen, FEET).interior().size() == 17, "fixture is exactly 17 not 12 cells");
        check(detect(oneMore, new Pos(3, 1, 3)).interior().size() == 125, "126th never retained");
        check(detect(room(3, 5, 3), FEET).valid(), "bbox height 5 inclusive");
        check(detect(room(3, 6, 3), FEET).reason() == Reason.TOO_LARGE, "bbox height 6 rejected");
        check(detect(room(3, 2, 9), FEET).valid(), "bbox depth 9 inclusive");
        check(detect(room(3, 2, 10), FEET).reason() == Reason.TOO_LARGE, "bbox depth 10 rejected");
        check(detect(room(6, 1, 3), FEET).reason() == Reason.TOO_SMALL, "18 cells without height-two are too small");
    }

    private static void boundaryAndFailureClosed() {
        TestWorld unloaded = room(3, 2, 3);
        Pos neighbor = new Pos(-1, 1, 1); unloaded.unload(neighbor);
        check(detect(unloaded, FEET).reason() == Reason.UNLOADED, "unloaded boundary is fail closed");

        TestWorld exception = room(3, 2, 3); exception.throwOnRead.add(new Pos(0, 0, 0));
        List<String> diagnostics = new ArrayList<>();
        ShelterDetector detector = new ShelterDetector((dimension, pos, error) -> diagnostics.add(dimension + " " + pos));
        check(!detector.detect(DIM, FEET, exception).valid(), "read exception never returns true");
        check(diagnostics.size() == 1 && diagnostics.get(0).contains(DIM), "read exception has dev diagnostic");
        check(diagnostics.get(0).contains("x=0, y=0, z=0"), "read diagnostic includes attempted coordinates");
        TestWorld startMissing = room(3, 2, 3); startMissing.unload(FEET);
        check(detect(startMissing, FEET).reason() == Reason.UNLOADED && startMissing.reads == 0, "unloaded start never read");
        TestWorld outdoors = new TestWorld(); outdoors.put(FEET, Cell.air());
        check(detect(outdoors, FEET).reason() == Reason.OPEN_VOLUME, "known world boundary reports OPEN_VOLUME");
        TestWorld infinite = new TestWorld(); infinite.fallback = Cell.air();
        check(detect(infinite, FEET).reason() == Reason.TOO_LARGE && infinite.reads < 751, "unbounded air stops within bounded work");
        ShelterDetector brokenLogger = new ShelterDetector((dimension, pos, error) -> { throw new IllegalStateException("logger"); });
        check(!brokenLogger.detect(DIM, FEET, exception).valid(), "diagnostic exception cannot crash detector");
        Voxels.Provider nullRead = new Voxels.Provider() {
            public boolean isLoaded(String dimension, Pos p) { return true; }
            public Cell readLoaded(String dimension, Pos p) { return null; }
        };
        check(!detector.detect(DIM, FEET, nullRead).valid(), "null read fails closed");
    }

    private static void cacheLifecycleAndInvalidation() {
        ShelterCache cache = new ShelterCache(new ShelterDetector((dimension, pos, error) -> { }));
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000002");
        TestWorld world = room(3, 2, 3);
        check(cache.sample(first, DIM, FEET, 0, world).valid(), "cache stores first player's valid result");
        int reads = world.reads; check(cache.sample(first, DIM, FEET, 19, world).valid() && world.reads == reads, "age 19 cache hit");
        check(cache.sample(first, DIM, FEET, 20, world).valid() && world.reads > reads, "age 20 refreshes");
        check(cache.sample(second, DIM, FEET, 20, world).valid() && cache.size() == 2, "players have independent entries");
        cache.blockChanged(DIM, new Pos(-2, 1, 1));
        check(cache.size() == 2, "change outside bbox+1 does not invalidate");
        cache.blockChanged(DIM, new Pos(-1, 1, 1));
        check(cache.size() == 0, "change inside bbox+1 invalidates both players");
        check(cache.sample(first, DIM, FEET, 21, world).valid(), "invalidated cache reads fresh");
        check(cache.sample(first, "minecraft:the_nether", FEET, 22, world).valid(), "dimension change reads fresh");
        cache.dimensionUnloaded("minecraft:the_nether"); check(cache.size() == 0, "dimension unload destroys cache");
        check(cache.sample(first, DIM, FEET, 23, world).valid(), "reconnect/new sample does not use old dimension");
        cache.chunkUnloaded(DIM, 0, 0); check(cache.size() == 0, "chunk unload invalidates bbox+1 dependency");
        cache.sample(first, DIM, FEET, 24, world); cache.remove(first); check(cache.size() == 0, "disconnect clears player");
        cache.sample(first, DIM, FEET, 25, world);
        check(cache.sample(first, DIM, new Pos(2, 1, 1), 26, world).valid(), "feet movement refreshes per-player cache");
    }

    private static void deterministicOrderAndShapeMasks() {
        TestWorld world = room(3, 2, 3);
        ShelterSnapshot first = detect(world, FEET);
        List<Pos> firstOrder = List.copyOf(world.readOrder);
        world.clearRuntime(); world.readOrder.clear();
        ShelterSnapshot second = detect(world, FEET);
        check(first.valid() && second.valid() && first.interior().equals(second.interior()), "same DTO is deterministic");
        check(firstOrder.equals(world.readOrder), "BFS read order is deterministic");
        check(firstOrder.subList(1, 7).equals(Voxels.ORDER.stream().map(FEET::step).toList()), "exact first six DOWN UP NORTH SOUTH WEST EAST");
        Cell fence = CollisionMasks.classify(Kind.ORDINARY, List.of(new Box(0.4, 0, 0.4, 0.6, 1, 0.6)), false, true);
        check(fence.blockedFaces() == 0 && !fence.supports(Face.UP), "thin decoration leaks and is not support");
        Cell slab = CollisionMasks.classify(Kind.ORDINARY, List.of(new Box(0, 0, 0, 1, .5, 1)), false, true);
        check(slab.blockedFaces() == 0 && !slab.supports(Face.UP), "slab does not seal/support full face");
        Cell full = CollisionMasks.classify(Kind.ORDINARY, List.of(new Box(0, 0, 0, 1, 1, 1)), false, false);
        check(full.blockedFaces() == Voxels.ALL && full.supports(Face.UP), "full collision has paired all-face mask");
        Cell split = CollisionMasks.classify(Kind.ORDINARY, List.of(new Box(0, 0, 0, .5, 1, 1), new Box(.5, 0, 0, 1, 1, 1)), false, false);
        check(split.fullSolid(), "union of two collision boxes is full solid");
        Cell withHole = CollisionMasks.classify(Kind.CLOSED_DOOR, List.of(new Box(0, 0, 0, .4, 1, .2), new Box(.6, 0, 0, 1, 1, .2)), false, true);
        check(withHole.blockedFaces() == 0, "aggregate bbox does not conceal collision hole");
        check(CollisionMasks.classify(Kind.OPEN_DOOR, List.of(new Box(0, 0, 0, 1, 1, 1)), false, true).blockedFaces() == 0,
                "OPEN wins over collision fullness");
        check(CollisionMasks.classify(Kind.CLOSED_TRAPDOOR, List.of(new Box(0, 0, 0, 1, .2, 1)), true, true).blockedFaces() == 0,
                "waterlogged plane cannot seal");
        boolean immutable = false;
        try { first.interior().clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "snapshot interior immutable");
    }

    private static void columnAndDoorInclusionContract() {
        // A closed door can be a reachable interior cell through a tangential side. It still participates in
        // the occupied XZ column floor/roof checks; no special "doorway exemption" exists.
        TestWorld w = room(3, 2, 3);
        w.put(new Pos(1, 1, 1), door(Face.NORTH, false));
        w.put(new Pos(1, 2, 1), door(Face.NORTH, false));
        ShelterSnapshot s = detect(w, FEET);
        check(s.valid() && s.interior().size() == 18, "reachable door cells included without changing room volume");
        check(s.interior().contains(new Pos(1, 2, 1)), "upper door cell interior inclusion");
        w.put(new Pos(1, 0, 1), new Cell(false, false, false, false, Voxels.ALL, 0, false));
        check(detect(w, FEET).reason() == Reason.NO_FLOOR, "doorway column is not exempt from floor");
        w.put(new Pos(1, 0, 1), Cell.solid());
        w.put(new Pos(1, 3, 1), new Cell(false, false, false, false, Voxels.ALL, 0, false));
        check(detect(w, FEET).reason() == Reason.NO_ROOF, "doorway column is not exempt from roof");

        Set<Pos> cells = new HashSet<>();
        for (int x = 0; x < 3; x++) for (int z = 0; z < 3; z++) {
            int floor = (x + z) % 2;
            for (int y = floor + 1; y <= floor + 2; y++) cells.add(new Pos(x, y, z));
        }
        TestWorld stepped = sealed(cells);
        check(detect(stepped, new Pos(0, 1, 0)).valid(), "stepped floors/roofs checked per occupied column");
        // Remove two-column corner; no support needed for unused bbox corner.
        Set<Pos> lShape = new HashSet<>();
        for (int x = 0; x < 4; x++) for (int z = 0; z < 4; z++) if (x == 0 || z == 0 || x == 1) {
            lShape.add(new Pos(x, 1, z)); lShape.add(new Pos(x, 2, z));
        }
        TestWorld lRoom = sealed(lShape);
        lRoom.put(new Pos(3, 0, 3), Cell.water()); lRoom.put(new Pos(3, 3, 3), Cell.water());
        check(detect(lRoom, new Pos(0, 1, 0)).valid(), "unused bbox XZ corner not checked for floor/roof");
    }

    private static void cacheFailClosedAndBoundaries() {
        List<String> errors = new ArrayList<>();
        ShelterCache cache = new ShelterCache(new ShelterDetector((d, p, e) -> errors.add(p.toString())));
        UUID a = new UUID(0, 10), b = new UUID(0, 11);
        TestWorld w = room(3, 2, 3);
        ShelterSnapshot initial = cache.sample(a, DIM, FEET, 100, w);
        int reads = w.reads;
        check(cache.sample(a, DIM, FEET, 100, w) == initial && w.reads == reads, "same-tick cache hit");
        w.unload(new Pos(-1, 1, 1));
        check(cache.sample(a, DIM, FEET, 101, w).reason() == Reason.UNLOADED && cache.entry(a) == null, "cached true boundary unload fails closed even without event");
        w.unloaded.clear();
        check(cache.sample(a, DIM, FEET, 102, w).valid() && w.reads > reads, "reload immediately reads fresh");
        w.throwOnLoaded.add(FEET);
        check(!cache.sample(a, DIM, FEET, 103, w).valid() && cache.entry(a) == null && !errors.isEmpty(), "cached loaded-check exception fails closed and diagnosed");
        w.throwOnLoaded.clear(); cache.sample(a, DIM, FEET, 104, w);
        reads = w.reads;
        check(cache.sample(a, DIM, FEET, 103, w).valid() && w.reads > reads, "backwards tick refreshes");
        cache.sample(b, DIM, FEET, 103, w);
        cache.sample(a, DIM, new Pos(100, 1, 100), 104, w);
        check(cache.entry(a) != null && cache.entry(a).feetPos().equals(new Pos(100, 1, 100))
                && cache.entry(b) != null, "moving one player outside does not alter another");
        check(cache.entry(b).snapshot().valid(), "other player's shelter truth remains valid after movement");
        cache.blockChanged("another:dimension", new Pos(0, 3, 0));
        check(cache.entry(b) != null, "other dimension block changes ignored");
        w.put(new Pos(0, 3, 0), Cell.air()); cache.blockChanged(DIM, new Pos(0, 3, 0));
        check(!cache.sample(b, DIM, FEET, 104, w).valid(), "stationary player sees roof removal before TTL");
        TestWorld good = room(3, 2, 3);
        cache.sample(b, DIM, FEET, 105, good);
        cache.remove(b);
        int reconnectReads = good.reads;
        check(cache.entry(b) == null && cache.sample(b, DIM, FEET, 105, good).valid() && good.reads > reconnectReads,
                "same UUID reconnect requires new read");
        reads = good.reads;
        cache.sample(b, "other:dimension", FEET, 106, good);
        check(good.reads > reads && cache.entry(b).dimension().equals("other:dimension"), "dimension identity replaced not stale true");
        cache.clear(); check(cache.size() == 0, "server stop empties cache");
        check(new Voxels.Bounds(new Pos(-16, 1, 0), new Pos(-14, 2, 2)).touchesChunk(-2, 0), "bbox+1 negative chunk boundary");
        check(new Voxels.Bounds(new Pos(14, 1, 0), new Pos(15, 2, 2)).touchesChunk(1, 0), "bbox+1 positive neighbor chunk");
        for (Face f : Voxels.ORDER) {
            ShelterSnapshot room = detect(good, FEET);
            Pos edge = new Pos(f.dx < 0 ? room.interiorMin().x() - 1 : f.dx > 0 ? room.interiorMax().x() + 1 : 1,
                    f.dy < 0 ? room.interiorMin().y() - 1 : f.dy > 0 ? room.interiorMax().y() + 1 : 1,
                    f.dz < 0 ? room.interiorMin().z() - 1 : f.dz > 0 ? room.interiorMax().z() + 1 : 1);
            cache.sample(b, DIM, FEET, 200, good); cache.blockChanged(DIM, edge);
            check(cache.entry(b) == null, "inclusive bbox+1 invalidation " + f);
        }
    }

    private static void cacheDiagnosticsAreReadOnly() {
        ShelterCache cache = new ShelterCache(new ShelterDetector((d, p, e) -> { }));
        UUID player = new UUID(0, 991);
        TestWorld initial = room(3, 2, 3);
        ShelterSnapshot original = cache.sample(player, DIM, FEET, 15, initial);
        check(original.valid() && cache.size() == 1, "diagnostic cache fixture installed");

        TestWorld readError = room(3, 2, 3);
        readError.throwOnRead.add(FEET);
        ShelterSnapshot failedRead = cache.diagnose(DIM, FEET, readError);
        check(failedRead.reason() == Reason.OPEN_VOLUME, "diagnose reports detector read error OPEN_VOLUME");
        check(cache.size() == 1 && cache.entry(player).snapshot().equals(original), "read error diagnose leaves cache unchanged");

        TestWorld unloaded = room(3, 2, 3);
        unloaded.unload(FEET);
        ShelterSnapshot unloadedResult = cache.diagnose(DIM, FEET, unloaded);
        check(unloadedResult.reason() == Reason.UNLOADED, "diagnose preserves UNLOADED result");
        check(cache.size() == 1 && cache.entry(player).snapshot().equals(original), "unloaded diagnose leaves cache unchanged");

        TestWorld open = room(3, 2, 3);
        open.put(new Pos(-1, 1, 1), Cell.air());
        ShelterSnapshot openResult = cache.diagnose(DIM, FEET, open);
        check(openResult.reason() == Reason.OPEN_VOLUME, "diagnose preserves OPEN_VOLUME result");
        check(cache.size() == 1 && cache.entry(player).snapshot().equals(original), "open diagnose leaves cache unchanged");
    }

    private static TestWorld sealed(Set<Pos> interior) {
        TestWorld w = new TestWorld();
        for (Pos p : interior) for (Face f : Voxels.ORDER) if (!interior.contains(p.step(f))) w.put(p.step(f), Cell.solid());
        for (Pos p : interior) w.put(p, Cell.air());
        return w;
    }

    private static void shapeNeighborDependencies() {
        TestWorld world = room(3, 2, 3);
        Pos neighbor = new Pos(32, 1, 0);
        Voxels.Provider adapter = new Voxels.Provider() {
            public boolean isLoaded(String dimension, Pos pos) { return world.isLoaded(dimension, pos); }
            public Cell readLoaded(String dimension, Pos pos) { return world.readLoaded(dimension, pos); }
            public Set<Pos> dependencies() { return Set.of(neighbor); }
        };
        UUID player = new UUID(0, 12);
        ShelterCache cache = new ShelterCache(new ShelterDetector((d, p, e) -> { }));
        ShelterSnapshot found = cache.sample(player, DIM, FEET, 0, adapter);
        check(found.valid() && found.dependencies().contains(neighbor), "shape neighbor recorded as dependency");
        cache.blockChanged(DIM, neighbor);
        check(cache.entry(player) == null, "shape neighbor change invalidates beyond bbox+1");
        cache.sample(player, DIM, FEET, 1, adapter);
        cache.chunkUnloaded(DIM, 2, 0);
        check(cache.entry(player) == null, "shape neighbor chunk unload invalidates");
        cache.sample(player, DIM, FEET, 2, adapter);
        world.unload(neighbor);
        check(cache.sample(player, DIM, FEET, 3, adapter).reason() == Reason.UNLOADED,
                "cached true rechecks shape neighbor loaded status");
        check(cache.sample(player, DIM, FEET, 4, adapter).reason() == Reason.UNLOADED && cache.entry(player) == null,
                "first detection fails UNLOADED on missing shape neighbor");
    }

    private static void exposureContract() {
        LightExposurePolicy.Result shelteredDark = LightExposurePolicy.evaluate(
                new LightExposurePolicy.Input(0, true, true, true, false, false), 0, 0, 100_000);
        LightExposurePolicy.Result openDark = LightExposurePolicy.evaluate(
                new LightExposurePolicy.Input(0, true, false, true, false, false), 0, 0, 100_000);
        LightExposurePolicy.Result brightShelter = LightExposurePolicy.evaluate(
                new LightExposurePolicy.Input(9, true, true, true, false, false), 10, 0, 100_000);
        check(shelteredDark.exposure() == 1, "dark shelter remains +1");
        check(openDark.exposure() == 2, "dark open sky outside shelter is +2");
        check(brightShelter.exposure() == 8, "bright light remains -2 regardless of shelter");
    }

    private static TestWorld room(int width, int height, int depth) {
        TestWorld world = new TestWorld();
        int x0 = 0, y0 = 1, z0 = 0;
        for (int x = x0; x < x0 + width; x++) for (int y = y0; y < y0 + height; y++) for (int z = z0; z < z0 + depth; z++) {
            world.put(new Pos(x, y, z), Cell.air());
        }
        for (int x = x0 - 1; x <= x0 + width; x++) for (int z = z0 - 1; z <= z0 + depth; z++) {
            world.put(new Pos(x, y0 - 1, z), Cell.solid());
            world.put(new Pos(x, y0 + height, z), Cell.solid());
        }
        for (int y = y0; y < y0 + height; y++) for (int x = x0 - 1; x <= x0 + width; x++) {
            world.put(new Pos(x, y, z0 - 1), Cell.solid());
            world.put(new Pos(x, y, z0 + depth), Cell.solid());
        }
        for (int y = y0; y < y0 + height; y++) for (int z = z0; z < z0 + depth; z++) {
            world.put(new Pos(x0 - 1, y, z), Cell.solid());
            world.put(new Pos(x0 + width, y, z), Cell.solid());
        }
        return world;
    }

    private static TestWorld roomWithDoor(Face facing, Cell door, boolean upper) {
        TestWorld world = room(3, 2, 3);
        Pos p = switch (facing) {
            case NORTH -> new Pos(1, upper ? 2 : 1, -1);
            case SOUTH -> new Pos(1, upper ? 2 : 1, 3);
            case WEST -> new Pos(-1, upper ? 2 : 1, 1);
            case EAST -> new Pos(3, upper ? 2 : 1, 1);
            default -> throw new AssertionError(facing);
        };
        world.put(p, door);
        // The opposite half has the same FACING/OPEN collision shape in a consistent vanilla door pair.
        world.put(new Pos(p.x(), upper ? 1 : 2, p.z()), door);
        return world;
    }

    private static TestWorld roomWithTrapdoor(Cell trapdoor, boolean top) {
        TestWorld world = room(3, 2, 3);
        // Actual face: TOP trapdoor supports floor from above; BOTTOM trapdoor supports roof from below.
        world.put(new Pos(1, top ? 0 : 3, 1), trapdoor);
        return world;
    }

    // javap 26.2: Shapes.rotateHorizontal maps its UNROTATED input to NORTH, not SOUTH.
    // DoorBlock's boxZ(16,13,16) is z=13..16, so NORTH-facing panel lies on SOUTH face.
    // HALF does not participate. TrapDoor HALF.TOP selects DOWN shape (at y=13..16).
    private static Cell door(Face face, boolean open) {
        Box box = switch (face) {
            case SOUTH -> new Box(0, 0, 0, 1, 1, 3.0 / 16);
            case NORTH -> new Box(0, 0, 13.0 / 16, 1, 1, 1);
            case EAST -> new Box(0, 0, 0, 3.0 / 16, 1, 1);
            case WEST -> new Box(13.0 / 16, 0, 0, 1, 1, 1);
            default -> throw new AssertionError(face);
        };
        return CollisionMasks.classify(open ? Kind.OPEN_DOOR : Kind.CLOSED_DOOR, List.of(box), false, true);
    }

    private static Cell trapdoor(boolean top, boolean open) {
        Box box = top ? new Box(0, 13.0 / 16, 0, 1, 1, 1) : new Box(0, 0, 0, 1, 3.0 / 16, 1);
        return CollisionMasks.classify(open ? Kind.OPEN_TRAPDOOR : Kind.CLOSED_TRAPDOOR, List.of(box), false, true);
    }

    private static ShelterSnapshot detect(TestWorld world, Pos feet) {
        return new ShelterDetector((dimension, pos, error) -> { }).detect(DIM, feet, world);
    }
    private static void check(boolean value, String message) {
        tests++;
        if (!value) { throw new AssertionError(message); }
        System.out.println("PASS " + tests + " " + message);
    }
    private static long elapsed(long start) { return (System.nanoTime() - start) / 1_000_000; }
}
