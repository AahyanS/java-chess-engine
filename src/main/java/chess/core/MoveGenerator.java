package chess.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates moves for the side to move.
 *
 * <p>It works in two steps. First it lists every move the pieces could make by their
 * movement rules ("pseudo-legal" moves). Then, to keep only legal moves, it plays each
 * one and throws it away if it leaves the mover's own king in check.
 */
public final class MoveGenerator {
    private static final int[] PROMOTIONS = {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT};

    private MoveGenerator() {
    }

    public static List<Move> legalMoves(Board board) {
        List<Move> pseudo = new ArrayList<>(64);
        generate(board, pseudo, false);
        List<Move> legal = new ArrayList<>(pseudo.size());
        for (Move m : pseudo) {
            board.makeMove(m);
            if (!board.leftKingInCheck()) {
                legal.add(m);
            }
            board.unmakeMove();
        }
        return legal;
    }

    /**
     * Adds pseudo-legal moves to {@code out}. With {@code capturesOnly}, only captures
     * and promotions are produced (used by the AI's quiescence search).
     */
    public static void generate(Board board, List<Move> out, boolean capturesOnly) {
        int us = board.sideToMove();
        for (int sq = 0; sq < 64; sq++) {
            int piece = board.pieceAt(sq);
            if (piece == Piece.EMPTY || Piece.color(piece) != us) {
                continue;
            }
            switch (Piece.type(piece)) {
                case Piece.PAWN -> pawnMoves(board, sq, us, out, capturesOnly);
                case Piece.KNIGHT -> jumpMoves(board, sq, us, Attacks.KNIGHT[sq], out, capturesOnly);
                case Piece.BISHOP -> slideMoves(board, sq, us, 4, 8, out, capturesOnly);
                case Piece.ROOK -> slideMoves(board, sq, us, 0, 4, out, capturesOnly);
                case Piece.QUEEN -> slideMoves(board, sq, us, 0, 8, out, capturesOnly);
                case Piece.KING -> {
                    jumpMoves(board, sq, us, Attacks.KING[sq], out, capturesOnly);
                    if (!capturesOnly) {
                        castlingMoves(board, sq, us, out);
                    }
                }
                default -> throw new IllegalStateException("Unknown piece " + piece);
            }
        }
    }

    private static boolean isEnemy(Board board, int sq, int us) {
        int p = board.pieceAt(sq);
        return p != Piece.EMPTY && Piece.color(p) != us;
    }

    private static void pawnMoves(Board board, int sq, int us, List<Move> out, boolean capturesOnly) {
        int forward = us == Piece.WHITE ? 8 : -8;
        int startRank = us == Piece.WHITE ? 1 : 6;
        int lastRank = us == Piece.WHITE ? 7 : 0;
        int file = Square.file(sq);
        int one = sq + forward;

        if (board.pieceAt(one) == Piece.EMPTY) {
            if (Square.rank(one) == lastRank) {
                addPromotions(sq, one, out);
            } else if (!capturesOnly) {
                out.add(new Move(sq, one));
                int two = one + forward;
                if (Square.rank(sq) == startRank && board.pieceAt(two) == Piece.EMPTY) {
                    out.add(new Move(sq, two, Piece.EMPTY, Move.DOUBLE_PUSH));
                }
            }
        }
        for (int df = -1; df <= 1; df += 2) {
            if (file + df < 0 || file + df > 7) {
                continue;
            }
            int target = one + df;
            if (isEnemy(board, target, us)) {
                if (Square.rank(target) == lastRank) {
                    addPromotions(sq, target, out);
                } else {
                    out.add(new Move(sq, target));
                }
            } else if (target == board.epSquare()) {
                out.add(new Move(sq, target, Piece.EMPTY, Move.EN_PASSANT));
            }
        }
    }

    private static void addPromotions(int from, int to, List<Move> out) {
        for (int type : PROMOTIONS) {
            out.add(new Move(from, to, type, Move.NORMAL));
        }
    }

    private static void jumpMoves(Board board, int sq, int us, int[] targets, List<Move> out, boolean capturesOnly) {
        for (int t : targets) {
            int p = board.pieceAt(t);
            if (p == Piece.EMPTY ? !capturesOnly : Piece.color(p) != us) {
                out.add(new Move(sq, t));
            }
        }
    }

    private static void slideMoves(Board board, int sq, int us, int firstDir, int lastDir,
                                   List<Move> out, boolean capturesOnly) {
        for (int d = firstDir; d < lastDir; d++) {
            for (int t : Attacks.RAYS[sq][d]) {
                int p = board.pieceAt(t);
                if (p == Piece.EMPTY) {
                    if (!capturesOnly) {
                        out.add(new Move(sq, t));
                    }
                } else {
                    if (Piece.color(p) != us) {
                        out.add(new Move(sq, t));
                    }
                    break;
                }
            }
        }
    }

    private static void castlingMoves(Board board, int sq, int us, List<Move> out) {
        int them = 1 - us;
        int home = us == Piece.WHITE ? Square.parse("e1") : Square.parse("e8");
        if (sq != home || board.isSquareAttacked(sq, them)) {
            return;
        }
        int kingside = us == Piece.WHITE ? Board.WHITE_KINGSIDE : Board.BLACK_KINGSIDE;
        int queenside = us == Piece.WHITE ? Board.WHITE_QUEENSIDE : Board.BLACK_QUEENSIDE;
        if (board.hasRight(kingside)
                && board.pieceAt(sq + 1) == Piece.EMPTY && board.pieceAt(sq + 2) == Piece.EMPTY
                && !board.isSquareAttacked(sq + 1, them) && !board.isSquareAttacked(sq + 2, them)) {
            out.add(new Move(sq, sq + 2, Piece.EMPTY, Move.CASTLE));
        }
        if (board.hasRight(queenside)
                && board.pieceAt(sq - 1) == Piece.EMPTY && board.pieceAt(sq - 2) == Piece.EMPTY
                && board.pieceAt(sq - 3) == Piece.EMPTY
                && !board.isSquareAttacked(sq - 1, them) && !board.isSquareAttacked(sq - 2, them)) {
            out.add(new Move(sq, sq - 2, Piece.EMPTY, Move.CASTLE));
        }
    }

    /**
     * Counts the leaf positions reachable in exactly {@code depth} moves. This "perft"
     * number is published for many test positions, so matching it proves the move
     * generator handles every rule (castling, en passant, promotion, pins) correctly.
     */
    public static long perft(Board board, int depth) {
        if (depth == 0) {
            return 1;
        }
        List<Move> moves = legalMoves(board);
        if (depth == 1) {
            return moves.size();
        }
        long total = 0;
        for (Move m : moves) {
            board.makeMove(m);
            total += perft(board, depth - 1);
            board.unmakeMove();
        }
        return total;
    }
}
