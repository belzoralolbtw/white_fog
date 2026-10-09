package com.whitefog.tests.lightfix;

/** Regression model for non-cached shelter reasons and sample snapshots. */
public final class DiagnosticsModel {
    public record Shelter(String reason, boolean cached, long checkedAt) { }
    public record Sample(boolean sampled, long tick, int eyeLight, int portable, int before, int after) { }

    private DiagnosticsModel() { }

    public static Shelter preserveNonCachedReason(String actualReason) {
        return new Shelter(actualReason, false, Long.MIN_VALUE);
    }

    public static Sample noSample() {
        return new Sample(false, Long.MIN_VALUE, 0, 0, 0, 0);
    }

    public static boolean burning(boolean knownKind, int count, int remaining, boolean lit) {
        return knownKind && count == 1 && remaining > 0 && lit;
    }
}
