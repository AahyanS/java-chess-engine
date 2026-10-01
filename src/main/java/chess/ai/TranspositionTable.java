package chess.ai;

import chess.core.Move;

/**
 * A cache of positions the search has already looked at, keyed by the position's
 * Zobrist hash. The same position is often reached by different move orders
 * (1.e4 e5 2.Nf3 and 1.Nf3 e5 2.e4), so remembering the result saves a lot of work.
 * It also remembers the best move found, which is tried first next time.
 */
final class TranspositionTable {
    static final int EXACT = 0;
    /** The real score is at least the stored score (the search was cut off early). */
    static final int LOWER_BOUND = 1;
    /** The real score is at most the stored score (no move beat alpha). */
    static final int UPPER_BOUND = 2;

    record Entry(long key, int depth, int score, int flag, Move bestMove) {
    }

    private final Entry[] entries;
    private final int mask;

    TranspositionTable(int sizePowerOfTwo) {
        entries = new Entry[1 << sizePowerOfTwo];
        mask = entries.length - 1;
    }

    Entry probe(long key) {
        Entry e = entries[(int) key & mask];
        return e != null && e.key() == key ? e : null;
    }

    void store(long key, int depth, int score, int flag, Move bestMove) {
        int index = (int) key & mask;
        Entry old = entries[index];
        // Keep deeper results for the same position; otherwise the newest wins.
        if (old == null || old.key() != key || depth >= old.depth()) {
            entries[index] = new Entry(key, depth, score, flag, bestMove);
        }
    }

    void clear() {
        java.util.Arrays.fill(entries, null);
    }
}
