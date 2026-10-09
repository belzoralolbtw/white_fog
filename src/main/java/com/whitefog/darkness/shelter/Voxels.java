package com.whitefog.darkness.shelter;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Read-only DTO boundary. No Minecraft types, name-based walls, or implicit chunk loads. */
public final class Voxels {
    private Voxels() { }

    public enum Face {
        DOWN(0, -1, 0), UP(0, 1, 0), NORTH(0, 0, -1), SOUTH(0, 0, 1), WEST(-1, 0, 0), EAST(1, 0, 0);
        public final int dx, dy, dz;
        Face(int dx, int dy, int dz) { this.dx = dx; this.dy = dy; this.dz = dz; }
        public int bit() { return 1 << ordinal(); }
        public Face opposite() { return values()[ordinal() ^ 1]; }
    }

    public static final List<Face> ORDER = List.of(Face.DOWN, Face.UP, Face.NORTH, Face.SOUTH, Face.WEST, Face.EAST);
    public static final int ALL = 63;

    public record Pos(int x, int y, int z) {
        public Pos step(Face face) {
            return new Pos(Math.addExact(x, face.dx), Math.addExact(y, face.dy), Math.addExact(z, face.dz));
        }
    }

    public record Bounds(Pos min, Pos max) {
        public Bounds {
            Objects.requireNonNull(min); Objects.requireNonNull(max);
            if (min.x > max.x || min.y > max.y || min.z > max.z) { throw new IllegalArgumentException("Inverted bbox"); }
        }
        public static Bounds at(Pos pos) { return new Bounds(pos, pos); }
        public Bounds include(Pos p) {
            return new Bounds(new Pos(Math.min(min.x, p.x), Math.min(min.y, p.y), Math.min(min.z, p.z)),
                    new Pos(Math.max(max.x, p.x), Math.max(max.y, p.y), Math.max(max.z, p.z)));
        }
        public boolean exceedsLimits() {
            return (long) max.x - min.x + 1 > 9 || (long) max.y - min.y + 1 > 5 || (long) max.z - min.z + 1 > 9;
        }
        public boolean containsExpanded(Pos p) {
            return p.x >= (long) min.x - 1 && p.x <= (long) max.x + 1
                    && p.y >= (long) min.y - 1 && p.y <= (long) max.y + 1
                    && p.z >= (long) min.z - 1 && p.z <= (long) max.z + 1;
        }
        public boolean touchesChunk(int x, int z) {
            return x >= Math.floorDiv((long) min.x - 1, 16) && x <= Math.floorDiv((long) max.x + 1, 16)
                    && z >= Math.floorDiv((long) min.z - 1, 16) && z <= Math.floorDiv((long) max.z + 1, 16);
        }
    }

    /** blockedFaces = traversal barriers; supportFaces = real complete collision boundary faces.
     * They differ for decorations: leak search ignores partial collision, endpoint support does not.
     * standingSpace is the adapter's feet fit check; clearSpace requires empty collision for height-2.
     * outside denotes a known world/build-height boundary, not an invented "outdoor" heuristic.
     */
    public record Cell(boolean fluid, boolean fullSolid, boolean standingSpace, boolean clearSpace,
                       int blockedFaces, int supportFaces, boolean outside) {
        public Cell {
            if ((blockedFaces & ~ALL) != 0 || (supportFaces & ~ALL) != 0) { throw new IllegalArgumentException("Face mask"); }
            if (fluid && (blockedFaces != 0 || supportFaces != 0 || standingSpace || clearSpace)) {
                throw new IllegalArgumentException("Fluid cannot seal or provide standing space");
            }
            if (fullSolid && (blockedFaces != ALL || standingSpace || clearSpace)) {
                throw new IllegalArgumentException("Full solid consistency");
            }
        }
        public boolean blocks(Face f) { return (blockedFaces & f.bit()) != 0; }
        public boolean supports(Face f) { return !fluid && (supportFaces & f.bit()) != 0; }
        public static Cell air() { return new Cell(false, false, true, true, 0, 0, false); }
        public static Cell solid() { return new Cell(false, true, false, false, ALL, ALL, false); }
        public static Cell water() { return new Cell(true, false, false, false, 0, 0, false); }
        public static Cell exterior() { return new Cell(false, false, false, false, 0, 0, true); }
    }

    /** Must be confined to one server sample/thread. Every read must itself recheck loaded status.
     * Shape adapters must record any additional neighbor reads through this same boundary.
     */
    public interface Provider {
        boolean isLoaded(String dimension, Pos pos);
        Cell readLoaded(String dimension, Pos pos);
        default Set<Pos> dependencies() { return Set.of(); }
    }

    @FunctionalInterface
    public interface Diagnostics {
        void failed(String dimension, Pos pos, RuntimeException error);
    }

    public static boolean blocked(Cell from, Cell to, Face direction) {
        return from.blocks(direction) || to.blocks(direction.opposite());
    }
}
