package com.whitefog.darkness.shelter;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import com.whitefog.darkness.shelter.ShelterSnapshot.Reason;
import com.whitefog.darkness.shelter.Voxels.Pos;
import com.whitefog.darkness.shelter.Voxels.Provider;

/** Server-thread-only player runtime cache. No persisted truth and no shared cross-player entries. */
public final class ShelterCache {
    public static final int TTL_TICKS = 20;
    public record Entry(Pos feetPos, String dimension, long checkedAt, ShelterSnapshot snapshot) { }
    private final Map<UUID, Entry> entries = new HashMap<>();
    private final ShelterDetector detector;
    public ShelterCache(ShelterDetector detector) { this.detector = Objects.requireNonNull(detector); }

    public ShelterSnapshot sample(UUID player, String dimension, Pos feet, long tick, Provider provider) {
        Objects.requireNonNull(player); Objects.requireNonNull(dimension); Objects.requireNonNull(feet);
        Objects.requireNonNull(provider);
        Entry entry = entries.get(player);
        long age = entry == null ? -1 : tick - entry.checkedAt;
        if (entry != null && entry.dimension.equals(dimension) && entry.feetPos.equals(feet)
                && tick >= entry.checkedAt && age >= 0 && age < TTL_TICKS) {
            Pos checking = feet;
            try {
                // Includes solid boundary voxels and endpoint support reads, not merely interior chunks.
                for (Pos pos : entry.snapshot.dependencies()) {
                    checking = pos;
                    if (!provider.isLoaded(dimension, pos)) {
                        entries.remove(player);
                        return failed(entry.snapshot, Reason.UNLOADED);
                    }
                }
                return entry.snapshot;
            } catch (RuntimeException error) {
                entries.remove(player);
                detector.report(dimension, checking, error);
                return failed(entry.snapshot, Reason.OPEN_VOLUME);
            }
        }
        entries.remove(player); // Dimension/feet/clock changes discard old true before any read.
        ShelterSnapshot snapshot = detector.detect(dimension, feet, provider);
        // Never retain unread/exception/world-edge failures; reload/recovery must read afresh immediately.
        if (snapshot.reason() != Reason.UNLOADED && snapshot.reason() != Reason.OPEN_VOLUME) {
            entries.put(player, new Entry(feet, dimension, tick, snapshot));
        }
        return snapshot;
    }

    /** Runs the detector without consulting or changing the runtime cache. */
    public ShelterSnapshot diagnose(String dimension, Pos feet, Provider provider) {
        Objects.requireNonNull(dimension); Objects.requireNonNull(feet); Objects.requireNonNull(provider);
        return detector.detect(dimension, feet, provider);
    }

    private static ShelterSnapshot failed(ShelterSnapshot old, Reason reason) {
        return new ShelterSnapshot(false, old.interiorMin(), old.interiorMax(), reason, old.interior(), old.dependencies());
    }
    public Entry entry(UUID player) { return entries.get(player); }
    public int size() { return entries.size(); }
    public void remove(UUID player) { entries.remove(player); } // disconnect/reconnect/respawn/dimension event
    public void clear() { entries.clear(); } // server stop
    public void blockChanged(String dimension, Pos pos) {
        entries.values().removeIf(e -> e.dimension.equals(dimension)
                && (e.snapshot.bbox().containsExpanded(pos) || e.snapshot.dependencies().contains(pos)));
    }
    public void chunkUnloaded(String dimension, int chunkX, int chunkZ) {
        entries.values().removeIf(e -> e.dimension.equals(dimension) && (e.snapshot.bbox().touchesChunk(chunkX, chunkZ)
                || e.snapshot.dependencies().stream().anyMatch(p -> Math.floorDiv(p.x(), 16) == chunkX
                && Math.floorDiv(p.z(), 16) == chunkZ)));
    }
    public void dimensionUnloaded(String dimension) {
        entries.values().removeIf(e -> e.dimension.equals(dimension));
    }
}
