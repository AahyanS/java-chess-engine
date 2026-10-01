package chess.ui;

import chess.core.Move;
import chess.core.Piece;
import java.awt.Component;
import java.util.List;
import javax.swing.JOptionPane;

/** Asks which piece a pawn should promote to. */
final class PromotionDialog {
    private static final int[] ORDER = {Piece.QUEEN, Piece.ROOK, Piece.BISHOP, Piece.KNIGHT};
    private static final String[] NAMES = {"Queen", "Rook", "Bishop", "Knight"};

    private PromotionDialog() {
    }

    static Move choose(Component parent, List<Move> promotions) {
        int choice = JOptionPane.showOptionDialog(parent, "Promote pawn to:", "Promotion",
                JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, NAMES, NAMES[0]);
        if (choice < 0) {
            return null;
        }
        for (Move m : promotions) {
            if (m.promotion() == ORDER[choice]) {
                return m;
            }
        }
        return null;
    }
}
