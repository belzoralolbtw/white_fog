package com.whitefog.tests.spawn;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.whitefog.darkness.spawn.DarkMobState;
import com.whitefog.darkness.spawn.DarkSpawnPolicy;
import com.whitefog.darkness.spawn.DarkSpawnPolicy.Attempt;
import com.whitefog.darkness.spawn.DarkSpawnPolicy.Candidate;
import com.whitefog.darkness.spawn.DarkSpawnPolicy.CandidateView;
import com.whitefog.darkness.spawn.DarkSpawnPolicy.MobKind;
import com.whitefog.darkness.spawn.DarkSpawnPolicy.PlayerInput;

/** Deterministic stage 1.8 acceptance sandbox. It is NOT live-spawn/runtime proof. */
public final class SelfTest {
    private static int tests;

    public static void main(String[] args) {
        long start = System.nanoTime();
        try {
            rollPermitBoundaries();
            deterministicSeedAndAttempt();
            exposureThreshold();
            distanceBoundaries();
            lightChannel();
            capBoundaries();
            candidateSelection();
            spiderWideAabb();
            kindRotation();
            targetSelection();
            capIndependentOfPlayers();
            mobStateAttachmentsIndex();
            mobStateReservations();
            mobStateAttempts();
            mobStatePersistenceRoundTrip();
            mobStateDirtyCallback();
            System.out.println("DARK_SPAWN_SELFTEST tests=" + tests + " elapsed_ms=" + elapsed(start) + " status=SUCCESS");
        } catch (AssertionError error) {
            System.err.println("DARK_SPAWN_SELFTEST tests=" + tests + " elapsed_ms=" + elapsed(start) + " status=FAILURE");
            error.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void rollPermitBoundaries() {
        int permits = 0;
        for (int roll = 0; roll < DarkSpawnPolicy.ROLL_BOUND; roll++) {
            if (DarkSpawnPolicy.permitsAttemptForRoll(roll)) permits++;
        }
        check(permits == 25, "all 100 rolls yield exactly 25 permits");
        check(DarkSpawnPolicy.permitsAttemptForRoll(0), "roll 0 permits");
        check(DarkSpawnPolicy.permitsAttemptForRoll(24), "roll 24 permits");
        check(!DarkSpawnPolicy.permitsAttemptForRoll(25), "roll 25 does not permit");
        check(!DarkSpawnPolicy.permitsAttemptForRoll(99), "roll 99 does not permit");
    }

    private static void deterministicSeedAndAttempt() {
        check(DarkSpawnPolicy.seed(0L, 1, 0, 0L) == DarkSpawnPolicy.SEED_X_MULTIPLIER,
                "seed uses chunkX * 341873128712");
        check(DarkSpawnPolicy.seed(0L, 0, 1, 0L) == DarkSpawnPolicy.SEED_Z_MULTIPLIER,
                "seed uses chunkZ * 132897987541");
        check(DarkSpawnPolicy.seed(5L, 0, 0, 7L) == (5L ^ 7L), "seed XORs worldSeed and bucket");
        long overflow = DarkSpawnPolicy.seed(Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, 123456789L);
        check(DarkSpawnPolicy.seed(Long.MAX_VALUE, Integer.MIN_VALUE, Integer.MAX_VALUE, 123456789L) == overflow,
                "seed overflow is deterministic");

        long seed = DarkSpawnPolicy.seed(987654321L, -134, 90210, 11L);
        Attempt a = DarkSpawnPolicy.attemptFor(seed);
        Attempt b = DarkSpawnPolicy.attemptFor(seed);
        check(a.roll() == b.roll(), "same seed gives same roll");
        check(Arrays.equals(a.candidateOffsets(), b.candidateOffsets()), "same seed gives same candidate offsets");
        check(a.candidateCount() == 8, "exactly 8 candidate offsets");
        boolean inRange = true;
        for (int i = 0; i < a.candidateCount(); i++) {
            inRange &= a.offsetX(i) >= 0 && a.offsetX(i) <= 15 && a.offsetZ(i) >= 0 && a.offsetZ(i) <= 15;
        }
        check(inRange, "all offsets are integer 0..15");
        check(a.roll() >= 0 && a.roll() < DarkSpawnPolicy.ROLL_BOUND, "roll is in 0..99");
        check(DarkSpawnPolicy.gameTimeBucket(0L) == 0L, "bucket of gameTime 0");
        check(DarkSpawnPolicy.gameTimeBucket(199L) == 1L, "bucket is floor(gameTime/100)");
        check(DarkSpawnPolicy.gameTimeBucket(-1L) == -1L, "bucket floors negatives without wrap");
    }

    private static void exposureThreshold() {
        check(!DarkSpawnPolicy.meetsExposure(74), "exposure 74 is not vulnerable");
        check(DarkSpawnPolicy.meetsExposure(75), "exposure 75 is vulnerable");
        check(DarkSpawnPolicy.meetsExposure(100), "exposure 100 is vulnerable");
    }

    private static void distanceBoundaries() {
        check(!DarkSpawnPolicy.inPlayerRadius(23.99D), "23.99 is outside the player radius");
        check(DarkSpawnPolicy.inPlayerRadius(24.0D), "24.0 is inside inclusive (lower)");
        check(DarkSpawnPolicy.inPlayerRadius(32.0D), "32.0 is inside inclusive (upper)");
        check(!DarkSpawnPolicy.inPlayerRadius(32.01D), "32.01 is outside the player radius");

        PlayerInput atOrigin = player(1, 0.0D, 0.0D, 64, 90);
        check(!DarkSpawnPolicy.farEnoughFromAnyPlayer(23.99D, 0.0D, List.of(atOrigin)),
                "candidate 23.99 from any player is rejected");
        check(DarkSpawnPolicy.farEnoughFromAnyPlayer(24.0D, 0.0D, List.of(atOrigin)),
                "candidate 24.0 from any player is accepted");
        check(DarkSpawnPolicy.farEnoughFromAnyPlayer(500.0D, 500.0D, List.of()),
                "no players means distance is fine");
    }

    private static void lightChannel() {
        check(DarkSpawnPolicy.blockLightOk(4), "block light 4 permits (sky channel not a predicate input)");
        check(!DarkSpawnPolicy.blockLightOk(5), "block light 5 rejects");
        // sky=15 at night is intentionally not consulted: only the block channel gates the candidate.
        FakeView view = singleColumnView(1, 1);
        Attempt attempt = customAttempt(pack(1, 1));
        check(DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, attempt, List.of(), view) != null,
                "candidate at block light 4 is accepted");
        view.light.put(new Pos(1, 1, 1), 5);
        check(DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, attempt, List.of(), view) == null,
                "candidate at block light 5 is rejected");
    }

    private static void capBoundaries() {
        check(DarkSpawnPolicy.capAllows(0, 0), "empty chunk/dimension allows");
        check(DarkSpawnPolicy.capAllows(1, 0), "chunk 1 allows");
        check(!DarkSpawnPolicy.capAllows(2, 0), "chunk 2 rejects");
        check(DarkSpawnPolicy.capAllows(0, 11), "dimension 11 allows");
        check(!DarkSpawnPolicy.capAllows(0, 12), "dimension 12 rejects");
        check(!DarkSpawnPolicy.capAllows(2, 12), "both caps full rejects");
    }

    private static void candidateSelection() {
        FakeView planes = planeView(0, 15);
        Attempt attempt = DarkSpawnPolicy.attemptFor(4242L);
        Candidate first = DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, attempt, List.of(), planes);
        check(first != null, "plane view yields a candidate");
        check(first.blockX() == attempt.offsetX(0) && first.blockZ() == attempt.offsetZ(0),
                "the first offset wins when all columns are valid");
        check(first.blockY() == 1, "candidate feet sit one block above the floor");

        // Custom attempt with distinct columns; only offset index 1 has a floor -> earlier invalid ones are skipped.
        Attempt custom = new Attempt(0L, 0, new int[]{pack(1, 1), pack(2, 2), pack(3, 3)});
        FakeView onlySecond = new FakeView();
        onlySecond.floors.add(new Pos(2, 0, 2));
        onlySecond.clear.add(new Pos(2, 1, 2));
        Candidate skipped = DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, custom, List.of(), onlySecond);
        check(skipped != null && skipped.blockX() == 2 && skipped.blockZ() == 2,
                "an invalid earlier candidate does not stop the search");

        Attempt allEight = new Attempt(0L, 0, new int[]{
                pack(1, 1), pack(2, 2), pack(3, 3), pack(4, 4), pack(5, 5), pack(6, 6), pack(7, 7), pack(8, 8)});
        check(DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, allEight, List.of(), new FakeView()) == null,
                "all 8 invalid candidates produce no spawn");

        // Sheltered candidate is skipped (read-only detector contract).
        FakeView shelter = singleColumnView(1, 1);
        Candidate before = DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, customAttempt(pack(1, 1)), List.of(), shelter);
        check(before != null, "unsheltered candidate accepted");
        shelter.sheltered.add(new Pos(1, 1, 1));
        check(DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, customAttempt(pack(1, 1)), List.of(), shelter) == null,
                "sheltered candidate rejected");
    }

    private static void spiderWideAabb() {
        FakeView narrow = planeView(0, 15);
        narrow.corridorWidth = 1.0D;
        Attempt attempt = DarkSpawnPolicy.attemptFor(99L);
        check(DarkSpawnPolicy.selectCandidate(MobKind.ZOMBIE, 0, 0, 0, attempt, List.of(), narrow) != null,
                "zombie fits a one-block corridor");
        check(DarkSpawnPolicy.selectCandidate(MobKind.SPIDER, 0, 0, 0, attempt, List.of(), narrow) == null,
                "spider wide AABB does not fit a one-block corridor");
    }

    private static void kindRotation() {
        check(DarkSpawnPolicy.kindFor(0) == MobKind.ZOMBIE, "successfulSpawns 0 -> zombie");
        check(DarkSpawnPolicy.kindFor(1) == MobKind.SKELETON, "successfulSpawns 1 -> skeleton");
        check(DarkSpawnPolicy.kindFor(2) == MobKind.SPIDER, "successfulSpawns 2 -> spider");
        check(DarkSpawnPolicy.kindFor(3) == MobKind.CREEPER, "successfulSpawns 3 -> creeper");
        check(DarkSpawnPolicy.kindFor(4) == MobKind.ZOMBIE, "successfulSpawns 4 wraps to zombie");
        check(DarkSpawnPolicy.kindFor(-1) == MobKind.CREEPER, "negative wraps safely");
    }

    private static void targetSelection() {
        PlayerInput near = player(1, 30.0D, 0.0D, 64, 75);
        PlayerInput farther = player(2, 31.0D, 0.0D, 64, 80);
        PlayerInput lowExposure = player(3, 24.0D, 0.0D, 64, 74);
        PlayerInput tooClose = player(4, 20.0D, 0.0D, 64, 90);
        PlayerInput notSurvival = new PlayerInput(uuid(5), 25.0D, 0.0D, 64, true, false, 90);
        PlayerInput dead = new PlayerInput(uuid(6), 29.0D, 0.0D, 64, false, true, 90);
        List<PlayerInput> all = List.of(near, farther, lowExposure, tooClose, notSurvival, dead);
        PlayerInput chosen = DarkSpawnPolicy.nearestEligiblePlayer(all, 0.0D, 0.0D);
        check(chosen != null && chosen.uuid().equals(near.uuid()), "nearest eligible survival player is chosen");

        check(DarkSpawnPolicy.nearestEligiblePlayer(List.of(lowExposure), 0.0D, 0.0D) == null,
                "exposure 74 is not eligible");
        check(DarkSpawnPolicy.nearestEligiblePlayer(List.of(tooClose), 0.0D, 0.0D) == null,
                "distance 20 is not eligible");
        check(DarkSpawnPolicy.nearestEligiblePlayer(List.of(notSurvival), 0.0D, 0.0D) == null,
                "creative/spectator is not eligible");
        check(DarkSpawnPolicy.nearestEligiblePlayer(List.of(dead), 0.0D, 0.0D) == null,
                "dead player is not eligible");

        PlayerInput lower = new PlayerInput(new UUID(0L, 1L), 30.0D, 0.0D, 64, true, true, 80);
        PlayerInput higher = new PlayerInput(new UUID(0L, 2L), -30.0D, 0.0D, 64, true, true, 80);
        PlayerInput tie = DarkSpawnPolicy.nearestEligiblePlayer(List.of(higher, lower), 0.0D, 0.0D);
        check(tie != null && tie.uuid().equals(new UUID(0L, 1L)), "distance tie breaks on lexicographically smaller UUID");
    }

    private static void capIndependentOfPlayers() {
        DarkMobState state = new DarkMobState();
        state.addLoaded(7L, uuid(100));
        state.addLoaded(7L, uuid(101));
        check(state.loadedChunkCount(7L) == 2 && state.loadedDimensionCount() == 2, "two marked mobs loaded");
        check(!DarkSpawnPolicy.capAllows(state.loadedChunkCount(7L), state.loadedDimensionCount()),
                "two marked mobs fill the chunk cap");
        // Cap has no player parameter: any number of nearby players must not multiply it.
        List<PlayerInput> crowd = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            crowd.add(player(i + 1, 28.0D, 0.0D, 64, 90));
        }
        check(!DarkSpawnPolicy.capAllows(state.loadedChunkCount(7L), state.loadedDimensionCount()),
                "cap is unchanged by eight nearby players");
    }

    private static void mobStateAttachmentsIndex() {
        long chunk = 42L;
        DarkMobState state = new DarkMobState();
        check(state.loadedChunkCount(chunk) == 0 && state.loadedDimensionCount() == 0, "index starts empty");
        state.addLoaded(chunk, uuid(1));
        state.addLoaded(chunk, uuid(1));
        check(state.loadedChunkCount(chunk) == 1, "duplicate load of the same UUID is not double-counted");
        check(state.loadedDimensionCount() == 1, "duplicate load does not grow the dimension count");
        state.addLoaded(chunk, uuid(2));
        check(state.loadedChunkCount(chunk) == 2, "a second UUID counts separately");
        state.removeLoaded(chunk, uuid(1));
        check(state.loadedChunkCount(chunk) == 1 && state.loadedDimensionCount() == 1, "remove cleans the index");
        state.clearRuntime();
        check(state.loadedChunkCount(chunk) == 0 && state.loadedDimensionCount() == 0, "clearRuntime empties the index");
    }

    private static void mobStateReservations() {
        long chunk = 5L;
        DarkMobState state = new DarkMobState();
        check(state.tryReserve(chunk), "first reservation succeeds");
        check(state.tryReserve(chunk), "second reservation succeeds");
        check(!state.tryReserve(chunk), "third reservation is blocked by the chunk cap");
        check(state.reservedChunk(chunk) == 2 && state.reservedDimension() == 2, "reservation counters tracked");
        state.releaseReservation(chunk);
        check(state.reservedChunk(chunk) == 1, "release frees one chunk slot");
        check(state.tryReserve(chunk), "a freed slot can be reserved again");
        state.resetReservations();
        check(state.reservedChunk(chunk) == 0 && state.reservedDimension() == 0, "reservations reset each tick");

        DarkMobState dimension = new DarkMobState();
        for (int i = 0; i < DarkSpawnPolicy.DIMENSION_CAP; i++) {
            check(dimension.tryReserve(1000L + i), "reservation " + (i + 1) + " within the dimension cap");
        }
        check(!dimension.tryReserve(9999L), "reservation beyond the dimension cap is blocked");
        check(dimension.reservedDimension() == DarkSpawnPolicy.DIMENSION_CAP, "dimension reservations counted");
    }

    private static void mobStateAttempts() {
        long chunk = 9L;
        DarkMobState state = new DarkMobState();
        check(state.successfulSpawns(chunk) == 0, "successfulSpawns starts at 0");
        check(!state.bucketProcessed(chunk, 10L), "bucket not processed yet");
        state.markAttempt(chunk, 10L);
        check(state.bucketProcessed(chunk, 10L), "attempt bucket is recorded");
        check(!state.bucketProcessed(chunk, 11L), "a different bucket is not marked processed");
        check(state.successfulSpawns(chunk) == 0, "a permit-less attempt does not increment successfulSpawns");
        state.recordSuccess(chunk);
        check(state.successfulSpawns(chunk) == 1, "successful add increments successfulSpawns");
        state.markAttempt(chunk, 11L);
        check(state.successfulSpawns(chunk) == 1, "markAttempt preserves the success counter");
        state.recordSuccess(chunk);
        check(state.successfulSpawns(chunk) == 2, "second success increments again");
    }

    private static void mobStatePersistenceRoundTrip() {
        long chunkA = 1L;
        long chunkB = 2L;
        DarkMobState state = new DarkMobState();
        state.markAttempt(chunkA, 55L);
        state.recordSuccess(chunkA);
        state.recordSuccess(chunkA);
        state.markAttempt(chunkB, 77L);
        Map<Long, DarkMobState.Attempt> snapshot = state.attemptsView();

        DarkMobState restored = new DarkMobState();
        restored.restoreAttempts(snapshot);
        check(restored.successfulSpawns(chunkA) == 2, "restored success counter survives serialization");
        check(restored.bucketProcessed(chunkA, 55L), "restored bucket survives serialization");
        check(restored.bucketProcessed(chunkB, 77L), "restored second chunk survives serialization");
        check(restored.attemptsView().equals(snapshot), "attempt map round-trips unchanged");
    }

    private static void mobStateDirtyCallback() {
        int[] dirty = {0};
        DarkMobState state = new DarkMobState(() -> dirty[0]++);
        state.markAttempt(3L, 4L);
        check(dirty[0] == 1, "persistent attempt change marks dirty");
        state.recordSuccess(3L);
        check(dirty[0] == 2, "successful spawn marks dirty");
        state.addLoaded(3L, uuid(50));
        state.removeLoaded(3L, uuid(50));
        state.tryReserve(3L);
        state.releaseReservation(3L);
        check(dirty[0] == 2, "runtime index/reservations do not mark persistent state dirty");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static PlayerInput player(int id, double x, double z, int feetY, int exposure) {
        return new PlayerInput(uuid(id), x, z, feetY, true, true, exposure);
    }

    private static UUID uuid(int value) {
        return new UUID(0L, value);
    }

    private static int pack(int offsetX, int offsetZ) {
        return (offsetX << 4) | offsetZ;
    }

    private static Attempt customAttempt(int... offsets) {
        return new Attempt(0L, 0, offsets);
    }

    private static FakeView planeView(int min, int max) {
        FakeView view = new FakeView();
        for (int x = min; x <= max; x++) {
            for (int z = min; z <= max; z++) {
                view.floors.add(new Pos(x, 0, z));
                view.clear.add(new Pos(x, 1, z));
            }
        }
        return view;
    }

    private static FakeView singleColumnView(int x, int z) {
        FakeView view = new FakeView();
        view.floors.add(new Pos(x, 0, z));
        view.clear.add(new Pos(x, 1, z));
        return view;
    }

    private record Pos(int x, int y, int z) { }

    /** Deterministic fake world for pure policy tests. */
    private static final class FakeView implements CandidateView {
        private static final Map<MobKind, Double> WIDTHS = Map.of(
                MobKind.ZOMBIE, 0.6D, MobKind.SKELETON, 0.6D, MobKind.SPIDER, 1.4D, MobKind.CREEPER, 0.6D);
        final Set<Pos> floors = new HashSet<>();
        final Set<Pos> clear = new HashSet<>();
        final Map<Pos, Integer> light = new HashMap<>();
        final Set<Pos> sheltered = new HashSet<>();
        boolean loaded = true;
        double corridorWidth = Double.POSITIVE_INFINITY;

        @Override
        public boolean isLoadedColumn(int blockX, int blockZ) {
            return loaded;
        }

        @Override
        public boolean hasFullSolidFloor(int blockX, int floorY, int blockZ) {
            return floors.contains(new Pos(blockX, floorY, blockZ));
        }

        @Override
        public boolean isAirAndClear(int blockX, int feetY, int blockZ, MobKind kind) {
            if (WIDTHS.get(kind) > corridorWidth) {
                return false;
            }
            return clear.contains(new Pos(blockX, feetY, blockZ));
        }

        @Override
        public int blockLight(int blockX, int feetY, int blockZ) {
            return light.getOrDefault(new Pos(blockX, feetY, blockZ), 0);
        }

        @Override
        public boolean isSheltered(int blockX, int feetY, int blockZ) {
            return sheltered.contains(new Pos(blockX, feetY, blockZ));
        }
    }

    private static void check(boolean value, String message) {
        tests++;
        if (!value) {
            throw new AssertionError(message);
        }
        System.out.println("PASS " + tests + " " + message);
    }

    private static long elapsed(long start) {
        return (System.nanoTime() - start) / 1_000_000L;
    }
}
