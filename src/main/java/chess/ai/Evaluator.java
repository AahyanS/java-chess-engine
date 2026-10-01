package chess.ai;

import chess.core.Board;
import chess.core.Piece;
import chess.core.Square;

/**
 * Scores a position without looking ahead. Scores are in "centipawns" (100 = one pawn)
 * from the point of view of the side to move: positive means that side is better.
 *
 * <p>The score is the sum of
 * <ul>
 *   <li><b>material</b>: what each piece is worth (pawn 100, knight 320, ...);</li>
 *   <li><b>piece-square tables</b>: a bonus or penalty for where each piece stands
 *       (knights like the center, pawns like to advance, the king likes to hide in the
 *       middlegame but come out in the endgame);</li>
 *   <li><b>bishop pair</b>: a small bonus for keeping both bishops.</li>
 * </ul>
 * The tables are based on Tomasz Michniewski's "Simplified Evaluation Function".
 */
public final class Evaluator {
    public static final int[] PIECE_VALUES = {0, 100, 320, 330, 500, 900, 0};
    private static final int BISHOP_PAIR = 30;

    // Tables are written as a chessboard seen from White's side: the first row is rank 8.
    private static final int[] PAWN = {
         0,  0,  0,  0,  0,  0,  0,  0,
        50, 50, 50, 50, 50, 50, 50, 50,
        10, 10, 20, 30, 30, 20, 10, 10,
         5,  5, 10, 25, 25, 10,  5,  5,
         0,  0,  0, 20, 20,  0,  0,  0,
         5, -5,-10,  0,  0,-10, -5,  5,
         5, 10, 10,-20,-20, 10, 10,  5,
         0,  0,  0,  0,  0,  0,  0,  0,
    };
    private static final int[] KNIGHT = {
        -50,-40,-30,-30,-30,-30,-40,-50,
        -40,-20,  0,  0,  0,  0,-20,-40,
        -30,  0, 10, 15, 15, 10,  0,-30,
        -30,  5, 15, 20, 20, 15,  5,-30,
        -30,  0, 15, 20, 20, 15,  0,-30,
        -30,  5, 10, 15, 15, 10,  5,-30,
        -40,-20,  0,  5,  5,  0,-20,-40,
        -50,-40,-30,-30,-30,-30,-40,-50,
    };
    private static final int[] BISHOP = {
        -20,-10,-10,-10,-10,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5, 10, 10,  5,  0,-10,
        -10,  5,  5, 10, 10,  5,  5,-10,
        -10,  0, 10, 10, 10, 10,  0,-10,
        -10, 10, 10, 10, 10, 10, 10,-10,
        -10,  5,  0,  0,  0,  0,  5,-10,
        -20,-10,-10,-10,-10,-10,-10,-20,
    };
    private static final int[] ROOK = {
          0,  0,  0,  0,  0,  0,  0,  0,
          5, 10, 10, 10, 10, 10, 10,  5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
         -5,  0,  0,  0,  0,  0,  0, -5,
          0,  0,  0,  5,  5,  0,  0,  0,
    };
    private static final int[] QUEEN = {
        -20,-10,-10, -5, -5,-10,-10,-20,
        -10,  0,  0,  0,  0,  0,  0,-10,
        -10,  0,  5,  5,  5,  5,  0,-10,
         -5,  0,  5,  5,  5,  5,  0, -5,
          0,  0,  5,  5,  5,  5,  0, -5,
        -10,  5,  5,  5,  5,  5,  0,-10,
        -10,  0,  5,  0,  0,  0,  0,-10,
        -20,-10,-10, -5, -5,-10,-10,-20,
    };
    private static final int[] KING_MIDDLEGAME = {
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -30,-40,-40,-50,-50,-40,-40,-30,
        -20,-30,-30,-40,-40,-30,-30,-20,
        -10,-20,-20,-20,-20,-20,-20,-10,
         20, 20,  0,  0,  0,  0, 20, 20,
         20, 30, 10,  0,  0, 10, 30, 20,
    };
    private static final int[] KING_ENDGAME = {
        -50,-40,-30,-20,-20,-30,-40,-50,
        -30,-20,-10,  0,  0,-10,-20,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 30, 40, 40, 30,-10,-30,
        -30,-10, 20, 30, 30, 20,-10,-30,
        -30,-30,  0,  0,  0,  0,-30,-30,
        -50,-30,-30,-30,-30,-30,-30,-50,
    };
    private static final int[][] TABLES = {null, PAWN, KNIGHT, BISHOP, ROOK, QUEEN};

    /** Non-pawn material at the start of the game, used to blend middlegame and endgame. */
    private static final int OPENING_MATERIAL = 2 * (2 * 320 + 2 * 330 + 2 * 500 + 900);

    private Evaluator() {
    }

    public static int evaluate(Board board) {
        int[] score = new int[2];
        int[] bishops = new int[2];
        int nonPawnMaterial = 0;
        for (int sq = 0; sq < 64; sq++) {
            int piece = board.pieceAt(sq);
            int type = Piece.type(piece);
            if (piece == Piece.EMPTY || type == Piece.KING) {
                continue;
            }
            int color = Piece.color(piece);
            score[color] += PIECE_VALUES[type] + TABLES[type][tableIndex(sq, color)];
            if (type != Piece.PAWN) {
                nonPawnMaterial += PIECE_VALUES[type];
            }
            if (type == Piece.BISHOP) {
                bishops[color]++;
            }
        }
        // phase: 1.0 with all pieces on the board, 0.0 when only kings and pawns remain.
        int phase = Math.min(nonPawnMaterial, OPENING_MATERIAL);
        for (int color = 0; color < 2; color++) {
            int idx = tableIndex(board.kingSquare(color), color);
            score[color] += (KING_MIDDLEGAME[idx] * phase + KING_ENDGAME[idx] * (OPENING_MATERIAL - phase))
                    / OPENING_MATERIAL;
            if (bishops[color] >= 2) {
                score[color] += BISHOP_PAIR;
            }
        }
        int us = board.sideToMove();
        return score[us] - score[1 - us];
    }

    /** Maps a board square to the table index, mirroring the table for Black. */
    private static int tableIndex(int square, int color) {
        int rank = color == Piece.WHITE ? 7 - Square.rank(square) : Square.rank(square);
        return rank * 8 + Square.file(square);
    }
}
