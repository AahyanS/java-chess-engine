package chess.core;

/**
 * Squares are numbered 0-63: a1 = 0, b1 = 1, ..., h1 = 7, a2 = 8, ..., h8 = 63.
 * So {@code file = square % 8} and {@code rank = square / 8}.
 */
public final class Square {
    public static final int NONE = -1;

    private Square() {
    }

    public static int of(int file, int rank) {
        return rank * 8 + file;
    }

    public static int file(int square) {
        return square & 7;
    }

    public static int rank(int square) {
        return square >> 3;
    }

    public static boolean onBoard(int file, int rank) {
        return file >= 0 && file < 8 && rank >= 0 && rank < 8;
    }

    public static String name(int square) {
        return "" + (char) ('a' + file(square)) + (char) ('1' + rank(square));
    }

    public static int parse(String name) {
        if (name.length() != 2) {
            throw new IllegalArgumentException("Bad square: " + name);
        }
        int file = name.charAt(0) - 'a';
        int rank = name.charAt(1) - '1';
        if (!onBoard(file, rank)) {
            throw new IllegalArgumentException("Bad square: " + name);
        }
        return of(file, rank);
    }
}
