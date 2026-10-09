package com.whitefog.darkness.shelter;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import com.whitefog.darkness.shelter.ShelterSnapshot.Reason;
import com.whitefog.darkness.shelter.Voxels.Bounds;
import com.whitefog.darkness.shelter.Voxels.Cell;
import com.whitefog.darkness.shelter.Voxels.Diagnostics;
import com.whitefog.darkness.shelter.Voxels.Face;
import com.whitefog.darkness.shelter.Voxels.Pos;
import com.whitefog.darkness.shelter.Voxels.Provider;

/** Bounded six-face FIFO search. No resources, chunks, or world voxels are changed.
 * Reference (algorithm idea, adapted not copied): Ad Astra FloodFill3D.run / TEST_FULL_SEAL:
 * https://github.com/terrarium-earth/Ad-Astra/blob/a9ae70c7b62af9389d52211bd6b6b69b510135f2/common/src/main/java/earth/terrarium/adastra/common/utils/floodfill/FloodFill3D.java
 * Differences required by ticket: inclusive 125, fixed order, loaded-only reads, paired masks,
 * column endpoints, minimum volume, and fail-closed diagnostics instead of oxygen distribution.
 */
public final class ShelterDetector {
    public static final int MAX_CELLS = 125;
    public static final int MIN_CELLS = 18;
    private final Diagnostics diagnostics;

    public ShelterDetector(Diagnostics diagnostics) { this.diagnostics = Objects.requireNonNull(diagnostics); }

    public ShelterSnapshot detect(String dimension, Pos feet, Provider provider) {
        Objects.requireNonNull(dimension); Objects.requireNonNull(feet); Objects.requireNonNull(provider);
        Search s = new Search(dimension, feet, provider);
        try {
            Cell start = s.read(feet);
            if (start == null) { return s.result(Reason.UNLOADED); }
            if (start.outside() || start.fluid() || start.fullSolid() || !start.standingSpace()) {
                return s.result(Reason.INVALID_START);
            }
            s.interior.add(feet);
            s.queue.add(feet);
            while (!s.queue.isEmpty()) {
                Pos pos = s.queue.removeFirst();
                Cell current = s.read(pos);
                if (current == null) { return s.result(Reason.UNLOADED); }
                for (Face face : Voxels.ORDER) {
                    s.attempted = pos;
                    Pos next = pos.step(face);
                    Cell neighbor = s.read(next);
                    if (neighbor == null) { return s.result(Reason.UNLOADED); }
                    if (Voxels.blocked(current, neighbor, face)) { continue; }
                    if (neighbor.outside()) { return s.result(Reason.OPEN_VOLUME); }
                    if (s.interior.contains(next)) { continue; }
                    Bounds candidate = s.bounds.include(next);
                    if (s.interior.size() == MAX_CELLS || candidate.exceedsLimits()) {
                        return s.result(Reason.TOO_LARGE); // Reject BEFORE adding the 126th/out-of-bbox cell.
                    }
                    s.bounds = candidate;
                    s.interior.add(next);
                    s.queue.addLast(next);
                }
            }
            // Check ONLY occupied XZ columns, including reachable doorway cells, not the bbox's empty corners.
            record XZ(int x, int z) { }
            Map<XZ, int[]> columns = new LinkedHashMap<>();
            for (Pos p : s.interior) {
                int[] range = columns.computeIfAbsent(new XZ(p.x(), p.z()), ignored -> new int[]{p.y(), p.y()});
                range[0] = Math.min(range[0], p.y()); range[1] = Math.max(range[1], p.y());
            }
            for (Map.Entry<XZ, int[]> e : columns.entrySet()) {
                Pos bottom = new Pos(e.getKey().x(), e.getValue()[0], e.getKey().z());
                Cell floor = s.read(bottom.step(Face.DOWN));
                if (floor == null) { return s.result(Reason.UNLOADED); }
                if (!floor.supports(Face.UP)) { return s.result(Reason.NO_FLOOR); }
                Pos top = new Pos(e.getKey().x(), e.getValue()[1], e.getKey().z());
                Cell roof = s.read(top.step(Face.UP));
                if (roof == null) { return s.result(Reason.UNLOADED); }
                if (!roof.supports(Face.DOWN)) { return s.result(Reason.NO_ROOF); }
            }
            boolean freeHeightTwo = false;
            for (Pos p : s.interior) {
                Pos above = p.step(Face.UP);
                if (s.interior.contains(above) && s.cells.get(p).clearSpace() && s.cells.get(above).clearSpace()
                        && !Voxels.blocked(s.cells.get(p), s.cells.get(above), Face.UP)) {
                    freeHeightTwo = true; break;
                }
            }
            return s.result(s.interior.size() >= MIN_CELLS && freeHeightTwo ? Reason.VALID : Reason.TOO_SMALL);
        } catch (RuntimeException error) {
            report(dimension, s.attempted, error);
            return s.result(Reason.OPEN_VOLUME); // Ticket has no ERROR reason. Never cache a true on a read error.
        }
    }

    void report(String dimension, Pos pos, RuntimeException error) {
        try { diagnostics.failed(dimension, pos, error); }
        catch (RuntimeException ignored) { /* Diagnostics must not crash the server tick either. */ }
    }

    private static final class Search {
        final String dimension;
        final Provider provider;
        final Set<Pos> interior = new LinkedHashSet<>(), dependencies = new LinkedHashSet<>();
        final Map<Pos, Cell> cells = new LinkedHashMap<>();
        final ArrayDeque<Pos> queue = new ArrayDeque<>();
        Bounds bounds;
        Pos attempted;
        Search(String dimension, Pos feet, Provider provider) {
            this.dimension = dimension; this.provider = provider; bounds = Bounds.at(feet); attempted = feet;
        }
        Cell read(Pos pos) {
            attempted = pos;
            dependencies.add(pos);
            if (!provider.isLoaded(dimension, pos)) { return null; }
            Cell value = cells.get(pos);
            if (value == null) {
                value = Objects.requireNonNull(provider.readLoaded(dimension, pos), "Loaded voxel read returned null");
                // Collision adapters may read neighboring states through their loaded-only BlockGetter.
                for (Pos dependency : provider.dependencies()) {
                    dependencies.add(dependency);
                    attempted = dependency;
                    if (!provider.isLoaded(dimension, dependency)) { return null; }
                }
                cells.put(pos, value);
            }
            return value;
        }
        ShelterSnapshot result(Reason reason) {
            return new ShelterSnapshot(reason == Reason.VALID, bounds.min(), bounds.max(), reason, interior, dependencies);
        }
    }
}
