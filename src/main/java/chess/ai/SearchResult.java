package chess.ai;

import chess.core.Move;
import java.util.List;

/**
 * What the engine found. {@code score} is in centipawns from the side to move's point
 * of view; {@code mateIn} is non-zero when a forced mate was found (positive: we mate,
 * negative: we get mated). {@code principalVariation} is the line of play the engine
 * expects, starting with {@code bestMove}.
 */
public record SearchResult(Move bestMove, int score, int mateIn, int depth, long nodes, long millis,
                           List<Move> principalVariation) {

    public long nodesPerSecond() {
        return millis == 0 ? nodes * 1000 : nodes * 1000 / millis;
    }

    /** Human-readable score such as {@code +1.25} or {@code mate in 3}. */
    public String scoreText() {
        if (mateIn > 0) {
            return "mate in " + mateIn;
        }
        if (mateIn < 0) {
            return "mated in " + (-mateIn);
        }
        return String.format("%+.2f", score / 100.0);
    }
}
