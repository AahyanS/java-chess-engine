package chess.core;

import java.util.Random;

/**
 * Zobrist hashing: every (piece, square) pair, the side to move, each castling-rights
 * combination and each en passant file gets a random 64-bit number. A position's hash
 * is the XOR of the numbers for everything in it, so making a move only has to XOR a
 * few numbers in and out instead of rehashing the whole board.
 *
 * <p>The engine uses the hash to remember positions it has already searched and to
 * detect repeated positions.
 */
final class Zobrist {
    static final long[][] PIECE = new long[16][64];
    static final long[] CASTLING = new long[16];
    static final long[] EP_FILE = new long[8];
    static final long SIDE;

    static {
        Random random = new Random(0x422C);
        for (long[] squares : PIECE) {
            for (int i = 0; i < 64; i++) {
                squares[i] = random.nextLong();
            }
        }
        for (int i = 0; i < CASTLING.length; i++) {
            CASTLING[i] = random.nextLong();
        }
        for (int i = 0; i < EP_FILE.length; i++) {
            EP_FILE[i] = random.nextLong();
        }
        SIDE = random.nextLong();
    }

    private Zobrist() {
    }
}
