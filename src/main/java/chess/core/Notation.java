package chess.core;

import java.util.List;

/**
 * Converts moves to and from Standard Algebraic Notation (SAN), the notation used in
 * chess books and on chess websites: {@code e4}, {@code Nf3}, {@code exd5}, {@code O-O},
 * {@code e8=Q+}, {@code Qxf7#}.
 */
public final class Notation {
    private Notation() {
    }

    /** SAN for {@code move}, which must be legal in the board's current position. */
    public static String toSan(Board board, Move move) {
        String san = sanWithoutSuffix(board, move);
        board.makeMove(move);
        if (board.inCheck()) {
            san += board.legalMoves().isEmpty() ? "#" : "+";
        }
        board.unmakeMove();
        return san;
    }

    private static String sanWithoutSuffix(Board board, Move move) {
        if (move.isCastle()) {
            return move.to() > move.from() ? "O-O" : "O-O-O";
        }
        int type = Piece.type(board.pieceAt(move.from()));
        boolean capture = board.pieceAt(move.to()) != Piece.EMPTY || move.isEnPassant();
        StringBuilder sb = new StringBuilder();
        if (type == Piece.PAWN) {
            if (capture) {
                sb.append(Square.name(move.from()).charAt(0));
            }
        } else {
            sb.append(Piece.typeLetter(type));
            sb.append(disambiguation(board, move, type));
        }
        if (capture) {
            sb.append('x');
        }
        sb.append(Square.name(move.to()));
        if (move.isPromotion()) {
            sb.append('=').append(Piece.typeLetter(move.promotion()));
        }
        return sb.toString();
    }

    /** When two identical pieces can reach the same square, say which one moves (e.g. Nbd2). */
    private static String disambiguation(Board board, Move move, int type) {
        boolean ambiguous = false;
        boolean sameFile = false;
        boolean sameRank = false;
        for (Move other : board.legalMoves()) {
            if (other.to() == move.to() && other.from() != move.from()
                    && Piece.type(board.pieceAt(other.from())) == type) {
                ambiguous = true;
                sameFile |= Square.file(other.from()) == Square.file(move.from());
                sameRank |= Square.rank(other.from()) == Square.rank(move.from());
            }
        }
        if (!ambiguous) {
            return "";
        }
        String from = Square.name(move.from());
        if (!sameFile) {
            return from.substring(0, 1);
        }
        if (!sameRank) {
            return from.substring(1);
        }
        return from;
    }

    /**
     * Finds the legal move described by {@code text}, which may be SAN ({@code Nf3})
     * or coordinate notation ({@code g1f3}). Returns {@code null} if no legal move matches.
     */
    public static Move parse(Board board, String text) {
        String input = text.trim().replaceAll("[+#!?]", "").replace('0', 'O');
        List<Move> legal = board.legalMoves();
        for (Move m : legal) {
            if (m.toUci().equalsIgnoreCase(input)) {
                return m;
            }
        }
        for (Move m : legal) {
            if (sanWithoutSuffix(board, m).equals(input)) {
                return m;
            }
        }
        return null;
    }
}
