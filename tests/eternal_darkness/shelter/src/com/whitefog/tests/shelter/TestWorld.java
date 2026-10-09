package com.whitefog.tests.shelter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import com.whitefog.darkness.shelter.Voxels.Cell;
import com.whitefog.darkness.shelter.Voxels.Pos;
import com.whitefog.darkness.shelter.Voxels.Provider;

final class TestWorld implements Provider {
    final Map<Pos, Cell> cells = new HashMap<>();
    final Set<Pos> unloaded = new HashSet<>();
    final Set<Pos> throwOnRead = new HashSet<>();
    final Set<Pos> throwOnLoaded = new HashSet<>();
    final List<Pos> readOrder = new java.util.ArrayList<>();
    Cell fallback = Cell.exterior();
    int reads;

    @Override public boolean isLoaded(String dimension, Pos pos) {
        if (throwOnLoaded.contains(pos)) { throw new IllegalStateException("synthetic loaded failure " + pos); }
        return !unloaded.contains(pos);
    }
    @Override public Cell readLoaded(String dimension, Pos pos) {
        reads++;
        readOrder.add(pos);
        if (unloaded.contains(pos)) { throw new AssertionError("Tried to read unloaded cell " + pos); }
        if (throwOnRead.contains(pos)) { throw new IllegalStateException("synthetic read failure " + pos); }
        return cells.getOrDefault(pos, fallback);
    }
    void put(Pos pos, Cell cell) { cells.put(pos, cell); }
    void unload(Pos pos) { unloaded.add(pos); }
    void clearRuntime() { reads = 0; }
}
