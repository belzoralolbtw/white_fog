package com.whitefog.darkness.shelter;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import com.whitefog.darkness.shelter.Voxels.Cell;
import com.whitefog.darkness.shelter.Voxels.Face;

/** Pure collision-box classification, not a Minecraft runtime adapter.
 * Inputs are actual boxes + verified state category (OPEN forces permeability).
 * Adapted idea: Ad Astra FloodFill3D.TEST_FULL_SEAL, collision-derived side sealing:
 * https://github.com/terrarium-earth/Ad-Astra/blob/a9ae70c7b62af9389d52211bd6b6b69b510135f2/common/src/main/java/earth/terrarium/adastra/common/utils/floodfill/FloodFill3D.java
 * Written independently: union coverage rather than a first-AABB/bounds shortcut; partial decorations leak.
 */
public final class CollisionMasks {
    private CollisionMasks() { }
    public enum Kind { ORDINARY, CLOSED_DOOR, OPEN_DOOR, CLOSED_TRAPDOOR, OPEN_TRAPDOOR }
    public record Box(double x0, double y0, double z0, double x1, double y1, double z1) {
        public Box {
            if (!Double.isFinite(x0 + y0 + z0 + x1 + y1 + z1)
                    || x0 >= x1 || y0 >= y1 || z0 >= z1) { throw new IllegalArgumentException("Invalid box"); }
        }
        double low(int axis) { return axis == 0 ? x0 : axis == 1 ? y0 : z0; }
        double high(int axis) { return axis == 0 ? x1 : axis == 1 ? y1 : z1; }
    }

    public static Cell classify(Kind kind, List<Box> input, boolean fluid, boolean standingSpace) {
        List<Box> boxes = List.copyOf(input);
        if (fluid) { return Cell.water(); }
        int support = 0;
        for (Face face : Voxels.ORDER) {
            int axis = face.dx != 0 ? 0 : face.dy != 0 ? 1 : 2;
            double edge = face.dx + face.dy + face.dz < 0 ? 0 : 1;
            // A boundary face exists only where the solid actually reaches that boundary.
            List<Box> slice = boxes.stream().filter(b -> b.low(axis) <= edge && b.high(axis) >= edge).toList();
            if (covers(slice, axis)) { support |= face.bit(); }
        }
        boolean open = kind == Kind.OPEN_DOOR || kind == Kind.OPEN_TRAPDOOR;
        boolean full = !open && coversVolume(boxes);
        int blocked = full ? Voxels.ALL : kind == Kind.CLOSED_DOOR || kind == Kind.CLOSED_TRAPDOOR ? support : 0;
        // Open doors/trapdoors never become walls or endpoint support even if a rotated shape covers a face.
        return new Cell(false, full, !full && standingSpace, !full && boxes.isEmpty(),
                open ? 0 : blocked, open ? 0 : support, false);
    }

    private static boolean coversVolume(List<Box> boxes) {
        List<Double> cuts = cuts(boxes, 0);
        for (int i = 1; i < cuts.size(); i++) {
            double x = (cuts.get(i - 1) + cuts.get(i)) / 2;
            if (!covers(boxes.stream().filter(b -> b.x0 <= x && b.x1 >= x).toList(), 0)) { return false; }
        }
        return true;
    }

    private static boolean covers(List<Box> boxes, int excludedAxis) {
        int a = (excludedAxis + 1) % 3, b = (excludedAxis + 2) % 3;
        List<Double> ca = cuts(boxes, a), cb = cuts(boxes, b);
        for (int i = 1; i < ca.size(); i++) {
            for (int j = 1; j < cb.size(); j++) {
                double pa = (ca.get(i - 1) + ca.get(i)) / 2, pb = (cb.get(j - 1) + cb.get(j)) / 2;
                if (boxes.stream().noneMatch(box -> box.low(a) <= pa && box.high(a) >= pa
                        && box.low(b) <= pb && box.high(b) >= pb)) { return false; }
            }
        }
        return true;
    }

    private static List<Double> cuts(List<Box> boxes, int axis) {
        TreeSet<Double> cuts = new TreeSet<>(List.of(0.0, 1.0));
        for (Box box : boxes) {
            if (box.low(axis) > 0 && box.low(axis) < 1) { cuts.add(box.low(axis)); }
            if (box.high(axis) > 0 && box.high(axis) < 1) { cuts.add(box.high(axis)); }
        }
        return new ArrayList<>(cuts);
    }

}
