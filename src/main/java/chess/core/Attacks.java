package chess.core;

/**
 * Precomputed tables of where pieces can reach from each square, built once at
 * startup so move generation never has to redo the edge-of-board arithmetic.
 */
final class Attacks {
    /** Knight destinations from each square. */
    static final int[][] KNIGHT = new int[64][];
    /** King destinations from each square. */
    static final int[][] KING = new int[64][];
    /**
     * {@code RAYS[square][direction]} lists the squares along a straight line, nearest
     * first. Directions 0-3 are orthogonal (rook), 4-7 are diagonal (bishop).
     */
    static final int[][][] RAYS = new int[64][8][];

    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1},
        {1, 1}, {1, -1}, {-1, 1}, {-1, -1},
    };
    private static final int[][] KNIGHT_JUMPS = {
        {1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2},
    };

    static {
        for (int sq = 0; sq < 64; sq++) {
            KNIGHT[sq] = targets(sq, KNIGHT_JUMPS);
            KING[sq] = targets(sq, DIRECTIONS);
            for (int d = 0; d < 8; d++) {
                RAYS[sq][d] = ray(sq, DIRECTIONS[d]);
            }
        }
    }

    private Attacks() {
    }

    private static int[] targets(int sq, int[][] offsets) {
        int[] buffer = new int[offsets.length];
        int count = 0;
        for (int[] o : offsets) {
            int f = Square.file(sq) + o[0];
            int r = Square.rank(sq) + o[1];
            if (Square.onBoard(f, r)) {
                buffer[count++] = Square.of(f, r);
            }
        }
        return java.util.Arrays.copyOf(buffer, count);
    }

    private static int[] ray(int sq, int[] dir) {
        int[] buffer = new int[7];
        int count = 0;
        int f = Square.file(sq) + dir[0];
        int r = Square.rank(sq) + dir[1];
        while (Square.onBoard(f, r)) {
            buffer[count++] = Square.of(f, r);
            f += dir[0];
            r += dir[1];
        }
        return java.util.Arrays.copyOf(buffer, count);
    }
}
