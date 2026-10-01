package chess.core;

public enum GameStatus {
    ONGOING("Game in progress"),
    CHECKMATE("Checkmate"),
    STALEMATE("Draw by stalemate"),
    DRAW_FIFTY_MOVES("Draw by the fifty-move rule"),
    DRAW_REPETITION("Draw by threefold repetition"),
    DRAW_INSUFFICIENT_MATERIAL("Draw by insufficient material");

    private final String description;

    GameStatus(String description) {
        this.description = description;
    }

    public boolean isOver() {
        return this != ONGOING;
    }

    public String description() {
        return description;
    }
}
