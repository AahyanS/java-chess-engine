package chess.core;

import java.util.ArrayList;
import java.util.List;

/**
 * The full state of a chess game: where the pieces are, whose turn it is, castling
 * and en passant rights, the move counters, and the history needed to take moves
 * back and to spot repeated positions.
 *
 * <p>Moves are applied with {@link #makeMove} and reverted with {@link #unmakeMove}.
 * The AI search relies on this pair being fast and exact: it plays millions of moves
 * forward and backward on a single board instead of copying it.
 */
public final class Board {
    public static final String START_FEN = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    public static final int WHITE_KINGSIDE = 1;
    public static final int WHITE_QUEENSIDE = 2;
    public static final int BLACK_KINGSIDE = 4;
    public static final int BLACK_QUEENSIDE = 8;

    /** Castling rights that survive a piece moving from or to each square. */
    private static final int[] CASTLE_MASK = new int[64];

    static {
        java.util.Arrays.fill(CASTLE_MASK, 15);
        CASTLE_MASK[Square.parse("e1")] &= ~(WHITE_KINGSIDE | WHITE_QUEENSIDE);
        CASTLE_MASK[Square.parse("h1")] &= ~WHITE_KINGSIDE;
        CASTLE_MASK[Square.parse("a1")] &= ~WHITE_QUEENSIDE;
        CASTLE_MASK[Square.parse("e8")] &= ~(BLACK_KINGSIDE | BLACK_QUEENSIDE);
        CASTLE_MASK[Square.parse("h8")] &= ~BLACK_KINGSIDE;
        CASTLE_MASK[Square.parse("a8")] &= ~BLACK_QUEENSIDE;
    }

    /** What {@link #unmakeMove} needs to restore the position before a move. */
    private record Undo(Move move, int captured, int castling, int epSquare, int halfmoveClock, long hash) {
    }

    private final int[] squares = new int[64];
    private final int[] kingSquare = new int[2];
    private int sideToMove;
    private int castling;
    private int epSquare = Square.NONE;
    private int halfmoveClock;
    private int fullmoveNumber = 1;
    private long hash;
    private final List<Undo> history;

    public Board() {
        this(START_FEN);
    }

    public Board(String fen) {
        history = new ArrayList<>();
        loadFen(fen);
    }

    /** Independent copy, including move history (so undo and repetition still work). */
    public Board(Board other) {
        System.arraycopy(other.squares, 0, squares, 0, 64);
        kingSquare[0] = other.kingSquare[0];
        kingSquare[1] = other.kingSquare[1];
        sideToMove = other.sideToMove;
        castling = other.castling;
        epSquare = other.epSquare;
        halfmoveClock = other.halfmoveClock;
        fullmoveNumber = other.fullmoveNumber;
        hash = other.hash;
        history = new ArrayList<>(other.history);
    }

    // ------------------------------------------------------------------ FEN

    private void loadFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        if (parts.length < 4) {
            throw new IllegalArgumentException("FEN needs at least 4 fields: " + fen);
        }
        java.util.Arrays.fill(squares, Piece.EMPTY);
        kingSquare[0] = kingSquare[1] = Square.NONE;
        int rank = 7;
        int file = 0;
        for (char c : parts[0].toCharArray()) {
            if (c == '/') {
                rank--;
                file = 0;
            } else if (Character.isDigit(c)) {
                file += c - '0';
            } else {
                if (!Square.onBoard(file, rank)) {
                    throw new IllegalArgumentException("Bad FEN board: " + fen);
                }
                int piece = Piece.fromChar(c);
                int sq = Square.of(file, rank);
                squares[sq] = piece;
                if (Piece.type(piece) == Piece.KING) {
                    kingSquare[Piece.color(piece)] = sq;
                }
                file++;
            }
        }
        if (kingSquare[0] == Square.NONE || kingSquare[1] == Square.NONE) {
            throw new IllegalArgumentException("FEN must have both kings: " + fen);
        }
        sideToMove = parts[1].equals("w") ? Piece.WHITE : Piece.BLACK;
        castling = 0;
        if (parts[2].contains("K")) castling |= WHITE_KINGSIDE;
        if (parts[2].contains("Q")) castling |= WHITE_QUEENSIDE;
        if (parts[2].contains("k")) castling |= BLACK_KINGSIDE;
        if (parts[2].contains("q")) castling |= BLACK_QUEENSIDE;
        epSquare = parts[3].equals("-") ? Square.NONE : Square.parse(parts[3]);
        halfmoveClock = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        fullmoveNumber = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        history.clear();
        hash = computeHash();
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            int empty = 0;
            for (int file = 0; file < 8; file++) {
                int piece = squares[Square.of(file, rank)];
                if (piece == Piece.EMPTY) {
                    empty++;
                } else {
                    if (empty > 0) {
                        sb.append(empty);
                        empty = 0;
                    }
                    sb.append(Piece.toChar(piece));
                }
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (rank > 0) {
                sb.append('/');
            }
        }
        sb.append(sideToMove == Piece.WHITE ? " w " : " b ");
        String rights = (hasRight(WHITE_KINGSIDE) ? "K" : "") + (hasRight(WHITE_QUEENSIDE) ? "Q" : "")
                + (hasRight(BLACK_KINGSIDE) ? "k" : "") + (hasRight(BLACK_QUEENSIDE) ? "q" : "");
        sb.append(rights.isEmpty() ? "-" : rights);
        sb.append(' ').append(epSquare == Square.NONE ? "-" : Square.name(epSquare));
        sb.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    // ------------------------------------------------------------ accessors

    public int pieceAt(int square) {
        return squares[square];
    }

    public int sideToMove() {
        return sideToMove;
    }

    public int kingSquare(int color) {
        return kingSquare[color];
    }

    public int epSquare() {
        return epSquare;
    }

    public boolean hasRight(int castlingRight) {
        return (castling & castlingRight) != 0;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public long hash() {
        return hash;
    }

    /** Number of moves played on this board since it was set up. */
    public int ply() {
        return history.size();
    }

    /** The most recent move, or {@code null} at the start. */
    public Move lastMove() {
        return history.isEmpty() ? null : history.get(history.size() - 1).move();
    }

    /** All moves played since the board was set up, oldest first. */
    public List<Move> moveHistory() {
        List<Move> moves = new ArrayList<>(history.size());
        for (Undo u : history) {
            moves.add(u.move());
        }
        return moves;
    }

    // ----------------------------------------------------------- make/unmake

    public void makeMove(Move m) {
        int from = m.from();
        int to = m.to();
        int us = sideToMove;
        int piece = squares[from];
        int capSq = m.isEnPassant() ? to + (us == Piece.WHITE ? -8 : 8) : to;
        int captured = squares[capSq];

        history.add(new Undo(m, captured, castling, epSquare, halfmoveClock, hash));

        if (epSquare != Square.NONE) {
            hash ^= Zobrist.EP_FILE[Square.file(epSquare)];
        }
        hash ^= Zobrist.CASTLING[castling];

        if (captured != Piece.EMPTY) {
            hash ^= Zobrist.PIECE[captured][capSq];
            squares[capSq] = Piece.EMPTY;
        }

        int placed = m.isPromotion() ? Piece.make(us, m.promotion()) : piece;
        hash ^= Zobrist.PIECE[piece][from];
        squares[from] = Piece.EMPTY;
        squares[to] = placed;
        hash ^= Zobrist.PIECE[placed][to];

        if (Piece.type(piece) == Piece.KING) {
            kingSquare[us] = to;
            if (m.isCastle()) {
                boolean kingside = to > from;
                movePiece(kingside ? from + 3 : from - 4, kingside ? from + 1 : from - 1);
            }
        }

        castling &= CASTLE_MASK[from] & CASTLE_MASK[to];
        hash ^= Zobrist.CASTLING[castling];

        epSquare = m.isDoublePush() ? (from + to) / 2 : Square.NONE;
        if (epSquare != Square.NONE) {
            hash ^= Zobrist.EP_FILE[Square.file(epSquare)];
        }

        halfmoveClock = (Piece.type(piece) == Piece.PAWN || captured != Piece.EMPTY) ? 0 : halfmoveClock + 1;
        if (us == Piece.BLACK) {
            fullmoveNumber++;
        }
        sideToMove = 1 - us;
        hash ^= Zobrist.SIDE;
    }

    public void unmakeMove() {
        Undo u = history.remove(history.size() - 1);
        Move m = u.move();
        sideToMove = 1 - sideToMove;
        int us = sideToMove;
        if (us == Piece.BLACK) {
            fullmoveNumber--;
        }

        int moved = m.isPromotion() ? Piece.make(us, Piece.PAWN) : squares[m.to()];
        squares[m.to()] = Piece.EMPTY;
        squares[m.from()] = moved;
        if (u.captured() != Piece.EMPTY) {
            int capSq = m.isEnPassant() ? m.to() + (us == Piece.WHITE ? -8 : 8) : m.to();
            squares[capSq] = u.captured();
        }
        if (Piece.type(moved) == Piece.KING) {
            kingSquare[us] = m.from();
            if (m.isCastle()) {
                boolean kingside = m.to() > m.from();
                int rookFrom = kingside ? m.from() + 3 : m.from() - 4;
                int rookTo = kingside ? m.from() + 1 : m.from() - 1;
                squares[rookFrom] = squares[rookTo];
                squares[rookTo] = Piece.EMPTY;
            }
        }
        castling = u.castling();
        epSquare = u.epSquare();
        halfmoveClock = u.halfmoveClock();
        hash = u.hash();
    }

    private void movePiece(int from, int to) {
        int piece = squares[from];
        hash ^= Zobrist.PIECE[piece][from] ^ Zobrist.PIECE[piece][to];
        squares[to] = piece;
        squares[from] = Piece.EMPTY;
    }

    // ------------------------------------------------------- attacks & rules

    /** Is {@code square} attacked by any piece of color {@code by}? */
    public boolean isSquareAttacked(int square, int by) {
        int file = Square.file(square);
        // Pawns: look one rank "behind" the square from the attacker's point of view.
        int pawn = Piece.make(by, Piece.PAWN);
        int behind = by == Piece.WHITE ? square - 8 : square + 8;
        if (behind >= 0 && behind < 64) {
            if (file > 0 && squares[behind - 1] == pawn) return true;
            if (file < 7 && squares[behind + 1] == pawn) return true;
        }
        int knight = Piece.make(by, Piece.KNIGHT);
        for (int t : Attacks.KNIGHT[square]) {
            if (squares[t] == knight) return true;
        }
        int king = Piece.make(by, Piece.KING);
        for (int t : Attacks.KING[square]) {
            if (squares[t] == king) return true;
        }
        int queen = Piece.make(by, Piece.QUEEN);
        int rook = Piece.make(by, Piece.ROOK);
        int bishop = Piece.make(by, Piece.BISHOP);
        for (int d = 0; d < 8; d++) {
            int slider = d < 4 ? rook : bishop;
            for (int t : Attacks.RAYS[square][d]) {
                int p = squares[t];
                if (p != Piece.EMPTY) {
                    if (p == slider || p == queen) return true;
                    break;
                }
            }
        }
        return false;
    }

    public boolean inCheck() {
        return isSquareAttacked(kingSquare[sideToMove], 1 - sideToMove);
    }

    /** After a move, did the player who just moved leave their own king in check? */
    public boolean leftKingInCheck() {
        int mover = 1 - sideToMove;
        return isSquareAttacked(kingSquare[mover], sideToMove);
    }

    public List<Move> legalMoves() {
        return MoveGenerator.legalMoves(this);
    }

    public boolean isLegal(Move move) {
        return legalMoves().contains(move);
    }

    /** Has the current position occurred at least {@code times} times before? */
    public boolean isRepetition(int times) {
        int seen = 0;
        // Only positions since the last capture or pawn move can repeat, and only
        // those with the same side to move (every second entry).
        int limit = Math.max(0, history.size() - halfmoveClock);
        for (int i = history.size() - 2; i >= limit; i -= 2) {
            if (history.get(i).hash() == hash && ++seen >= times) {
                return true;
            }
        }
        return false;
    }

    public boolean isInsufficientMaterial() {
        int minors = 0;
        for (int p : squares) {
            int type = Piece.type(p);
            if (type == Piece.PAWN || type == Piece.ROOK || type == Piece.QUEEN) {
                return false;
            }
            if (type == Piece.KNIGHT || type == Piece.BISHOP) {
                minors++;
            }
        }
        return minors <= 1;
    }

    public GameStatus status() {
        if (legalMoves().isEmpty()) {
            return inCheck() ? GameStatus.CHECKMATE : GameStatus.STALEMATE;
        }
        if (halfmoveClock >= 100) {
            return GameStatus.DRAW_FIFTY_MOVES;
        }
        if (isRepetition(2)) {
            return GameStatus.DRAW_REPETITION;
        }
        if (isInsufficientMaterial()) {
            return GameStatus.DRAW_INSUFFICIENT_MATERIAL;
        }
        return GameStatus.ONGOING;
    }

    /** Recomputes the hash from scratch; used on setup and by tests to check the incremental hash. */
    long computeHash() {
        long h = 0;
        for (int sq = 0; sq < 64; sq++) {
            if (squares[sq] != Piece.EMPTY) {
                h ^= Zobrist.PIECE[squares[sq]][sq];
            }
        }
        h ^= Zobrist.CASTLING[castling];
        if (epSquare != Square.NONE) {
            h ^= Zobrist.EP_FILE[Square.file(epSquare)];
        }
        if (sideToMove == Piece.BLACK) {
            h ^= Zobrist.SIDE;
        }
        return h;
    }

    /** A text diagram of the board, white at the bottom. */
    public String toDiagram() {
        StringBuilder sb = new StringBuilder();
        for (int rank = 7; rank >= 0; rank--) {
            sb.append(rank + 1).append("  ");
            for (int file = 0; file < 8; file++) {
                sb.append(Piece.toChar(squares[Square.of(file, rank)])).append(' ');
            }
            sb.append('\n');
        }
        sb.append("   a b c d e f g h\n");
        return sb.toString();
    }

    @Override
    public String toString() {
        return toFen();
    }
}
