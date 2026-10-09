package com.whitefog.tests.lightfix;

import java.util.HashMap;
import java.util.Map;

/** Pure model of the two server drop paths and the block/item fuel round-trip. */
public final class RoundTripModel {
    public record Fuel(int remaining, boolean lit) { }

    private final Map<String, Fuel> store = new HashMap<>();
    private final Map<String, Fuel> blocks = new HashMap<>();

    public void place(String blockId, String pos, Fuel fuel) {
        Fuel normalized = new Fuel(Math.max(0, fuel.remaining()), fuel.remaining() > 0 && fuel.lit());
        blocks.put(pos, normalized);
        store.put(pos, normalized);
    }

    public Fuel customBreak(String pos) {
        Fuel stateBeforeRemoval = blocks.get(pos);
        blocks.remove(pos);
        return drop(pos, stateBeforeRemoval);
    }

    public Fuel vanillaBreak(String pos) {
        Fuel stateBeforeRemoval = blocks.get(pos);
        Fuel item = drop(pos, stateBeforeRemoval);
        blocks.remove(pos);
        return item;
    }

    private Fuel drop(String pos, Fuel stateBeforeRemoval) {
        Fuel record = store.remove(pos);
        if (record == null) {
            return new Fuel(0, false);
        }
        return new Fuel(record.remaining(), stateBeforeRemoval != null && stateBeforeRemoval.lit());
    }

    public Fuel block(String pos) {
        return blocks.get(pos);
    }

    public void placeAfterDrop(String blockId, String pos, Fuel item) {
        place(blockId, pos, item);
    }

    /** Historical bug model: the custom post-removal drop inferred lit from remaining. */
    public static Fuel oldFallbackDrop(Fuel record, boolean blockWasAlreadyRemoved) {
        return new Fuel(record.remaining(), blockWasAlreadyRemoved && record.remaining() > 0);
    }

    /** Context identity required by the production hook. */
    public record Context(String dimension, String kind, String pos, Fuel state) { }
}
