package chess.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class NotationTest {

    private static String san(String fen, String uci) {
        Board board = new Board(fen);
        return Notation.toSan(board, Notation.parse(board, uci));
    }

    @Test
    void writesBasicMoves() {
        assertEquals("e4", san(Board.START_FEN, "e2e4"));
        assertEquals("Nf3", san(Board.START_FEN, "g1f3"));
    }

    @Test
    void writesCapturesChecksAndMates() {
        assertEquals("exd5", san("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "e4d5"));
        assertEquals("Bb5+", san("rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2", "f1b5"));
        assertEquals("Qh4#", san("rnbqkbnr/pppp1ppp/8/4p3/6P1/5P2/PPPPP2P/RNBQKBNR b KQkq - 0 2", "d8h4"));
    }

    @Test
    void writesCastlingAndPromotion() {
        assertEquals("O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1g1"));
        assertEquals("O-O-O", san("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1", "e1c1"));
        assertEquals("a8=Q+", san("7k/P7/8/8/8/8/8/K7 w - - 0 1", "a7a8q"));
    }

    @Test
    void disambiguatesIdenticalPieces() {
        // Knights on b1 and f3 can both reach d2.
        assertEquals("Nbd2", san("4k3/8/8/8/8/5N2/8/1N2K3 w - - 0 1", "b1d2"));
        // Rooks on a1 and a5 can both reach a3.
        assertEquals("R1a3", san("4k3/8/8/R7/8/8/8/R3K3 w - - 0 1", "a1a3"));
    }

    @Test
    void parsesBothNotations() {
        Board board = new Board();
        assertEquals(Notation.parse(board, "e2e4"), Notation.parse(board, "e4"));
        assertEquals(Notation.parse(board, "g1f3"), Notation.parse(board, "Nf3"));
        assertNull(Notation.parse(board, "e5"));
        assertNull(Notation.parse(board, "hello"));
    }
}
