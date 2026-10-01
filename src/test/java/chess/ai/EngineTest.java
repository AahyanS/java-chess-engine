package chess.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import chess.core.Board;
import chess.core.Notation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tactical puzzles with a single correct answer that the engine must find. */
class EngineTest {

    @ParameterizedTest(name = "{0}: {2}")
    @CsvSource(delimiter = '|', value = {
        "back-rank mate in 1        | 6k1/5ppp/8/8/8/8/5PPP/3R2K1 w - - 0 1                            | Rd8#",
        "scholar's mate in 1        | r1bqkbnr/pppp1ppp/2n5/4p3/2B1P3/5Q2/PPPP1PPP/RNB1K1NR w KQkq - 0 1 | Qxf7#",
        "black mates in 1           | r5k1/8/8/8/8/8/5PPP/6K1 b - - 0 1                                | Ra1#",
        "smothered mate in 1        | 6rk/6pp/7N/8/8/8/6PP/6K1 w - - 0 1                               | Nf7#",
        "queen sacrifice, mate in 2 | 5r1k/6pp/7N/8/2Q5/8/6PP/6K1 w - - 0 1                            | Qg8+",
        "win the hanging queen      | rnb1kbnr/pppp1ppp/8/4p1q1/4P3/3P4/PPP2PPP/RNBQKBNR w KQkq - 0 1  | Bxg5",
        "knight fork wins the queen | 2q1k3/8/8/8/4N3/8/7P/4K3 w - - 0 1                               | Nd6+",
    })
    void findsTheWinningMove(String name, String fen, String expected) {
        Board board = new Board(fen);
        SearchResult result = new Engine().searchToDepth(board, 5);
        assertNotNull(result.bestMove());
        assertEquals(expected, Notation.toSan(board, result.bestMove()), name);
    }

    @Test
    void reportsMateDistance() {
        Board board = new Board("k7/8/1K6/8/8/8/8/7R w - - 0 1");
        SearchResult result = new Engine().searchToDepth(board, 4);
        assertEquals("Rh8#", Notation.toSan(board, result.bestMove()));
        assertEquals(1, result.mateIn());
    }

    @Test
    void timeLimitedSearchReturnsQuickly() {
        Board board = new Board();
        long start = System.currentTimeMillis();
        SearchResult result = new Engine().searchForTime(board, 300);
        long elapsed = System.currentTimeMillis() - start;
        assertNotNull(result.bestMove());
        assertTrue(board.isLegal(result.bestMove()));
        assertTrue(elapsed < 1500, "search took " + elapsed + " ms");
        assertTrue(result.depth() >= 3, "should reach a reasonable depth, got " + result.depth());
    }

    @Test
    void searchDoesNotChangeTheBoard() {
        Board board = new Board("r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1");
        String before = board.toFen();
        new Engine().searchToDepth(board, 4);
        assertEquals(before, board.toFen());
    }

    @Test
    void returnsNoMoveWhenCheckmated() {
        Board board = new Board("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
        SearchResult result = new Engine().searchToDepth(board, 3);
        assertEquals(null, result.bestMove());
    }
}
