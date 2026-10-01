package chess.core;

/**
 * A single move. {@code promotion} is a piece type (e.g. {@link Piece#QUEEN}) or
 * {@link Piece#EMPTY}. {@code flags} marks the special moves that need extra work
 * when the move is played.
 */
public record Move(int from, int to, int promotion, int flags) {
    public static final int NORMAL = 0;
    public static final int EN_PASSANT = 1;
    public static final int CASTLE = 2;
    public static final int DOUBLE_PUSH = 4;

    public Move(int from, int to) {
        this(from, to, Piece.EMPTY, NORMAL);
    }

    public boolean isEnPassant() {
        return (flags & EN_PASSANT) != 0;
    }

    public boolean isCastle() {
        return (flags & CASTLE) != 0;
    }

    public boolean isDoublePush() {
        return (flags & DOUBLE_PUSH) != 0;
    }

    public boolean isPromotion() {
        return promotion != Piece.EMPTY;
    }

    /** Long algebraic / UCI form, e.g. {@code e2e4} or {@code e7e8q}. */
    public String toUci() {
        String s = Square.name(from) + Square.name(to);
        if (isPromotion()) {
            s += Character.toLowerCase(Piece.typeLetter(promotion));
        }
        return s;
    }

    @Override
    public String toString() {
        return toUci();
    }
}
