package chess.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class BoardTest {

    private static Board play(String... sanMoves) {
        Board board = new Board();
        for (String san : sanMoves) {
            Move m = Notation.parse(board, san);
            if (m == null) {
                throw new AssertionError("Illegal move in test: " + san + " at " + board.toFen());
            }
            board.makeMove(m);
        }
        return board;
    }

    @Test
    void fenRoundTrips() {
        String[] fens = {
            Board.START_FEN,
            "r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1",
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3",
            "8/8/8/8/8/8/8/K6k b - - 42 99",
        };
        for (String fen : fens) {
            assertEquals(fen, new Board(fen).toFen());
        }
    }

    @Test
    void rejectsBadFen() {
        assertThrows(IllegalArgumentException.class, () -> new Board("not a fen"));
        assertThrows(IllegalArgumentException.class, () -> new Board("8/8/8/8/8/8/8/8 w - - 0 1"));
    }

    @Test
    void makeAndUnmakeRestoreEverythingDuringRandomGames() {
        Random random = new Random(422);
        for (int game = 0; game < 50; game++) {
            Board board = new Board();
            for (int ply = 0; ply < 120 && !board.status().isOver(); ply++) {
                List<Move> moves = board.legalMoves();
                Move m = moves.get(random.nextInt(moves.size()));
                String fenBefore = board.toFen();
                long hashBefore = board.hash();

                board.makeMove(m);
                assertEquals(board.computeHash(), board.hash(), "incremental hash after " + m);
                board.unmakeMove();
                assertEquals(fenBefore, board.toFen());
                assertEquals(hashBefore, board.hash());

                board.makeMove(m);
            }
        }
    }

    @Test
    void detectsFoolsMate() {
        Board board = play("f3", "e5", "g4", "Qh4#");
        assertTrue(board.inCheck());
        assertEquals(GameStatus.CHECKMATE, board.status());
    }

    @Test
    void detectsStalemate() {
        Board board = new Board("7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertFalse(board.inCheck());
        assertEquals(GameStatus.STALEMATE, board.status());
    }

    @Test
    void detectsThreefoldRepetition() {
        Board board = play("Nf3", "Nf6", "Ng1", "Ng8", "Nf3", "Nf6", "Ng1");
        assertEquals(GameStatus.ONGOING, board.status());
        board.makeMove(Notation.parse(board, "Ng8"));
        assertEquals(GameStatus.DRAW_REPETITION, board.status());
    }

    @Test
    void detectsInsufficientMaterial() {
        assertEquals(GameStatus.DRAW_INSUFFICIENT_MATERIAL, new Board("8/8/4k3/8/8/2KB4/8/8 w - - 0 1").status());
        assertEquals(GameStatus.ONGOING, new Board("8/8/4k3/8/8/2KR4/8/8 w - - 0 1").status());
    }

    @Test
    void detectsFiftyMoveRule() {
        assertEquals(GameStatus.DRAW_FIFTY_MOVES, new Board("8/8/4k3/8/8/2KR4/8/8 w - - 100 80").status());
    }

    @Test
    void castlingMovesTheRookAndRemovesRights() {
        Board board = new Board("r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        board.makeMove(Notation.parse(board, "O-O"));
        assertEquals(Piece.make(Piece.WHITE, Piece.KING), board.pieceAt(Square.parse("g1")));
        assertEquals(Piece.make(Piece.WHITE, Piece.ROOK), board.pieceAt(Square.parse("f1")));
        assertEquals(Piece.EMPTY, board.pieceAt(Square.parse("h1")));
        assertFalse(board.hasRight(Board.WHITE_KINGSIDE));
        assertFalse(board.hasRight(Board.WHITE_QUEENSIDE));
        assertTrue(board.hasRight(Board.BLACK_QUEENSIDE));
    }

    @Test
    void cannotCastleThroughCheck() {
        // The black rook on f8 attacks f1, which the king would pass through.
        Board board = new Board("4kr2/8/8/8/8/8/8/R3K2R w KQ - 0 1");
        assertNull(Notation.parse(board, "O-O"));
        assertTrue(board.isLegal(Notation.parse(board, "O-O-O")));
    }

    @Test
    void enPassantCapturesThePawnBehind() {
        Board board = play("e4", "a6", "e5", "d5");
        Move ep = Notation.parse(board, "exd6");
        assertTrue(ep.isEnPassant());
        board.makeMove(ep);
        assertEquals(Piece.EMPTY, board.pieceAt(Square.parse("d5")));
        assertEquals(Piece.make(Piece.WHITE, Piece.PAWN), board.pieceAt(Square.parse("d6")));
    }

    @Test
    void promotionOffersAllFourPieces() {
        Board board = new Board("8/P7/8/8/8/8/8/K6k w - - 0 1");
        long promotions = board.legalMoves().stream().filter(Move::isPromotion).count();
        assertEquals(4, promotions);
        board.makeMove(Notation.parse(board, "a8=N"));
        assertEquals(Piece.make(Piece.WHITE, Piece.KNIGHT), board.pieceAt(Square.parse("a8")));
    }

    @Test
    void copyIsIndependent() {
        Board original = play("e4", "e5");
        Board copy = new Board(original);
        copy.makeMove(Notation.parse(copy, "Nf3"));
        assertEquals(2, original.ply());
        assertEquals(3, copy.ply());
        copy.unmakeMove();
        assertEquals(original.toFen(), copy.toFen());
    }
}
