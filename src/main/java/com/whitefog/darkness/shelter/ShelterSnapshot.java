package com.whitefog.darkness.shelter;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import com.whitefog.darkness.shelter.Voxels.Bounds;
import com.whitefog.darkness.shelter.Voxels.Pos;

/** Immutable runtime result, including all loaded-read dependencies (never persistent state). */
public record ShelterSnapshot(boolean valid, Pos interiorMin, Pos interiorMax, Reason reason,
                              Set<Pos> interior, Set<Pos> dependencies) {
    public enum Reason { VALID, UNLOADED, TOO_LARGE, OPEN_VOLUME, NO_FLOOR, NO_ROOF, INVALID_START, TOO_SMALL }
    public ShelterSnapshot {
        Objects.requireNonNull(interiorMin); Objects.requireNonNull(interiorMax); Objects.requireNonNull(reason);
        if (valid != (reason == Reason.VALID)) { throw new IllegalArgumentException("valid/reason mismatch"); }
        interior = Collections.unmodifiableSet(new LinkedHashSet<>(interior));
        dependencies = Collections.unmodifiableSet(new LinkedHashSet<>(dependencies));
    }
    public Bounds bbox() { return new Bounds(interiorMin, interiorMax); }
}
