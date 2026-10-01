package chess.ui;

import chess.core.Board;
import chess.core.Move;
import chess.core.Piece;
import chess.core.Square;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.GlyphVector;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JPanel;

/**
 * Draws the board and lets the player pick a piece and a destination by clicking.
 * It knows nothing about the AI; it just reports the chosen move to a listener.
 */
public final class BoardPanel extends JPanel {
    private static final Color LIGHT = new Color(0xF0D9B5);
    private static final Color DARK = new Color(0xB58863);
    private static final Color LAST_MOVE = new Color(205, 210, 106, 170);
    private static final Color SELECTED = new Color(20, 85, 30, 120);
    private static final Color HINT = new Color(20, 85, 30, 110);
    private static final Color CHECK = new Color(220, 40, 40, 170);

    private Board board = new Board();
    private boolean flipped;
    private boolean interactive = true;
    private int selected = Square.NONE;
    private List<Move> selectedMoves = new ArrayList<>();
    private Consumer<Move> moveListener = m -> { };
    private Font pieceFont;

    public BoardPanel() {
        setPreferredSize(new Dimension(640, 640));
        pieceFont = findPieceFont();
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                handleClick(e.getX(), e.getY());
            }
        });
    }

    /** Uses a font that actually contains the chess glyphs, falling back to Serif. */
    private static Font findPieceFont() {
        String glyphs = "♚♛♜♝♞♟";
        for (String name : new String[] {"Segoe UI Symbol", "Apple Symbols", "DejaVu Sans", "Noto Sans Symbols2",
                "FreeSerif", "Serif"}) {
            Font f = new Font(name, Font.PLAIN, 48);
            if (f.canDisplayUpTo(glyphs) == -1) {
                return f;
            }
        }
        return new Font(Font.SERIF, Font.PLAIN, 48);
    }

    public void setBoard(Board board) {
        this.board = board;
        clearSelection();
        repaint();
    }

    public void setFlipped(boolean flipped) {
        this.flipped = flipped;
        repaint();
    }

    public boolean isFlipped() {
        return flipped;
    }

    /** When false (e.g. while the AI is thinking), clicks are ignored. */
    public void setInteractive(boolean interactive) {
        this.interactive = interactive;
        if (!interactive) {
            clearSelection();
        }
    }

    public void setMoveListener(Consumer<Move> listener) {
        this.moveListener = listener;
    }

    private void clearSelection() {
        selected = Square.NONE;
        selectedMoves = new ArrayList<>();
        repaint();
    }

    private int cellSize() {
        return Math.min(getWidth(), getHeight()) / 8;
    }

    private int squareAt(int x, int y) {
        int cell = cellSize();
        int col = x / cell;
        int row = y / cell;
        if (col < 0 || col > 7 || row < 0 || row > 7) {
            return Square.NONE;
        }
        int file = flipped ? 7 - col : col;
        int rank = flipped ? row : 7 - row;
        return Square.of(file, rank);
    }

    private void handleClick(int x, int y) {
        if (!interactive) {
            return;
        }
        int sq = squareAt(x, y);
        if (sq == Square.NONE) {
            return;
        }
        if (selected != Square.NONE) {
            List<Move> matches = selectedMoves.stream().filter(m -> m.to() == sq).toList();
            if (!matches.isEmpty()) {
                Move move = matches.size() == 1 ? matches.get(0) : PromotionDialog.choose(this, matches);
                clearSelection();
                if (move != null) {
                    moveListener.accept(move);
                }
                return;
            }
        }
        int piece = board.pieceAt(sq);
        if (piece != Piece.EMPTY && Piece.color(piece) == board.sideToMove() && sq != selected) {
            selected = sq;
            selectedMoves = board.legalMoves().stream().filter(m -> m.from() == sq).toList();
            repaint();
        } else {
            clearSelection();
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        int cell = cellSize();
        Move last = board.lastMove();
        int checkedKing = board.inCheck() ? board.kingSquare(board.sideToMove()) : Square.NONE;

        for (int sq = 0; sq < 64; sq++) {
            int x = col(sq) * cell;
            int y = row(sq) * cell;
            g2.setColor(isLight(sq) ? LIGHT : DARK);
            g2.fillRect(x, y, cell, cell);
            if (last != null && (sq == last.from() || sq == last.to())) {
                g2.setColor(LAST_MOVE);
                g2.fillRect(x, y, cell, cell);
            }
            if (sq == selected) {
                g2.setColor(SELECTED);
                g2.fillRect(x, y, cell, cell);
            }
            if (sq == checkedKing) {
                g2.setColor(CHECK);
                g2.fillOval(x + 2, y + 2, cell - 4, cell - 4);
            }
        }
        drawCoordinates(g2, cell);
        for (int sq = 0; sq < 64; sq++) {
            int piece = board.pieceAt(sq);
            if (piece != Piece.EMPTY) {
                drawPiece(g2, piece, col(sq) * cell, row(sq) * cell, cell);
            }
        }
        // Dots for empty target squares, rings for captures.
        for (Move m : selectedMoves) {
            int x = col(m.to()) * cell;
            int y = row(m.to()) * cell;
            g2.setColor(HINT);
            if (board.pieceAt(m.to()) == Piece.EMPTY) {
                int d = cell / 3;
                g2.fillOval(x + (cell - d) / 2, y + (cell - d) / 2, d, d);
            } else {
                g2.setStroke(new BasicStroke(cell / 12f));
                g2.drawOval(x + cell / 20, y + cell / 20, cell - cell / 10, cell - cell / 10);
            }
        }
        g2.dispose();
    }

    private int col(int sq) {
        return flipped ? 7 - Square.file(sq) : Square.file(sq);
    }

    private int row(int sq) {
        return flipped ? Square.rank(sq) : 7 - Square.rank(sq);
    }

    /** File letters along the bottom edge and rank numbers along the left edge. */
    private void drawCoordinates(Graphics2D g2, int cell) {
        g2.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(10, cell / 7)));
        for (int i = 0; i < 8; i++) {
            int file = flipped ? 7 - i : i;
            int bottomRank = flipped ? 7 : 0;
            g2.setColor(isLight(Square.of(file, bottomRank)) ? DARK : LIGHT);
            g2.drawString(String.valueOf((char) ('a' + file)), i * cell + cell - cell / 6, 8 * cell - cell / 20);

            int rank = flipped ? i : 7 - i;
            int leftFile = flipped ? 7 : 0;
            g2.setColor(isLight(Square.of(leftFile, rank)) ? DARK : LIGHT);
            g2.drawString(String.valueOf(rank + 1), cell / 20, i * cell + cell / 5);
        }
    }

    private static boolean isLight(int sq) {
        return (Square.file(sq) + Square.rank(sq)) % 2 == 1;
    }

    /** Draws a solid glyph filled white or black with a contrasting outline. */
    private void drawPiece(Graphics2D g2, int piece, int x, int y, int cell) {
        Font font = pieceFont.deriveFont((float) cell * 0.8f);
        GlyphVector gv = font.createGlyphVector(g2.getFontRenderContext(), Piece.glyph(Piece.type(piece)));
        Rectangle2D bounds = gv.getVisualBounds();
        double tx = x + (cell - bounds.getWidth()) / 2 - bounds.getX();
        double ty = y + (cell - bounds.getHeight()) / 2 - bounds.getY();
        Shape shape = AffineTransform.getTranslateInstance(tx, ty).createTransformedShape(gv.getOutline());
        boolean white = Piece.color(piece) == Piece.WHITE;
        g2.setColor(white ? Color.WHITE : new Color(0x222222));
        g2.fill(shape);
        g2.setColor(white ? new Color(0x222222) : new Color(0x888888));
        g2.setStroke(new BasicStroke(Math.max(1f, cell / 48f)));
        g2.draw(shape);
    }
}
