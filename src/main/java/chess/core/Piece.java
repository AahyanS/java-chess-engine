package chess.core;

/**
 * Pieces are stored as small integers so the board can be a plain {@code int[64]}.
 *
 * <p>The low three bits hold the piece type (1-6) and bit 3 holds the color, so a
 * white knight is {@code 2} and a black knight is {@code 2 | 8 = 10}. Zero means an
 * empty square.
 */
public final class Piece {
    public static final int EMPTY = 0;

    public static final int PAWN = 1;
    public static final int KNIGHT = 2;
    public static final int BISHOP = 3;
    public static final int ROOK = 4;
    public static final int QUEEN = 5;
    public static final int KING = 6;

    public static final int WHITE = 0;
    public static final int BLACK = 1;

    private static final String LETTERS = " PNBRQK";

    private Piece() {
    }

    public static int make(int color, int type) {
        return type | (color << 3);
    }

    public static int type(int piece) {
        return piece & 7;
    }

    public static int color(int piece) {
        return piece >> 3;
    }

    /** FEN letter: uppercase for white, lowercase for black. */
    public static char toChar(int piece) {
        if (piece == EMPTY) {
            return '.';
        }
        char c = LETTERS.charAt(type(piece));
        return color(piece) == WHITE ? c : Character.toLowerCase(c);
    }

    public static int fromChar(char c) {
        int type = LETTERS.indexOf(Character.toUpperCase(c));
        if (type <= 0) {
            throw new IllegalArgumentException("Not a piece letter: " + c);
        }
        return make(Character.isUpperCase(c) ? WHITE : BLACK, type);
    }

    /** Uppercase letter for a piece type, as used in algebraic notation (e.g. 'N'). */
    public static char typeLetter(int type) {
        return LETTERS.charAt(type);
    }

    /** The solid Unicode chess glyph for a piece type (the GUI colors it itself). */
    public static String glyph(int type) {
        return switch (type) {
            case KING -> "♚";
            case QUEEN -> "♛";
            case ROOK -> "♜";
            case BISHOP -> "♝";
            case KNIGHT -> "♞";
            case PAWN -> "♟";
            default -> "";
        };
    }

    public static String colorName(int color) {
        return color == WHITE ? "White" : "Black";
    }
}
