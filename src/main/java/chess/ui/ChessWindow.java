package chess.ui;

import chess.ai.Engine;
import chess.ai.SearchResult;
import chess.core.Board;
import chess.core.GameStatus;
import chess.core.Move;
import chess.core.Notation;
import chess.core.Piece;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.KeyStroke;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;

/**
 * The game window: the board on the left, and on the right the game status, the
 * move list, what the AI is thinking, and the controls.
 *
 * <p>The AI runs on a background thread (a {@link SwingWorker}) so the window stays
 * responsive while it thinks.
 */
public final class ChessWindow extends JFrame {

    /** How long the AI may think. Easy also caps how far ahead it looks. */
    public enum Difficulty {
        EASY("Easy", 2, 300),
        MEDIUM("Medium", 64, 1000),
        HARD("Hard", 64, 4000);

        final String label;
        final int maxDepth;
        final long millis;

        Difficulty(String label, int maxDepth, long millis) {
            this.label = label;
            this.maxDepth = maxDepth;
            this.millis = millis;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Engine engine = new Engine();
    private final BoardPanel boardPanel = new BoardPanel();
    private final JLabel statusLabel = new JLabel();
    private final JTextArea moveList = new JTextArea();
    private final JTextArea engineInfo = new JTextArea();
    private final JComboBox<Difficulty> difficultyBox = new JComboBox<>(Difficulty.values());

    private Board board = new Board();
    private final List<String> sanMoves = new ArrayList<>();
    private int humanColor = Piece.WHITE;
    /** Move number and side to move of the starting position, for numbering the move list. */
    private int firstMoveNumber = 1;
    private int firstMover = Piece.WHITE;
    /** Incremented whenever the game changes, so stale AI results can be ignored. */
    private int generation;
    private boolean thinking;

    public ChessWindow() {
        super("Java Chess Engine");
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        boardPanel.setMoveListener(this::humanMoved);

        add(boardPanel, BorderLayout.CENTER);
        add(buildSidePanel(), BorderLayout.EAST);
        setJMenuBar(buildMenuBar());
        difficultyBox.setSelectedItem(Difficulty.MEDIUM);

        pack();
        setLocationRelativeTo(null);
        newGame(Piece.WHITE);
    }

    public static void launch() {
        SwingUtilities.invokeLater(() -> new ChessWindow().setVisible(true));
    }

    // ------------------------------------------------------------- layout

    private JPanel buildSidePanel() {
        JPanel side = new JPanel(new BorderLayout(0, 8));
        side.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        side.setPreferredSize(new Dimension(300, 640));

        statusLabel.setFont(statusLabel.getFont().deriveFont(Font.BOLD, 16f));
        side.add(statusLabel, BorderLayout.NORTH);

        moveList.setEditable(false);
        moveList.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
        moveList.setLineWrap(true);
        moveList.setWrapStyleWord(true);
        JScrollPane movesScroll = new JScrollPane(moveList);
        movesScroll.setBorder(BorderFactory.createTitledBorder("Moves"));
        side.add(movesScroll, BorderLayout.CENTER);

        JPanel bottom = new JPanel(new BorderLayout(0, 8));
        engineInfo.setEditable(false);
        engineInfo.setLineWrap(true);
        engineInfo.setWrapStyleWord(true);
        engineInfo.setRows(6);
        engineInfo.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        engineInfo.setBorder(BorderFactory.createTitledBorder("AI thinking"));
        engineInfo.setOpaque(false);
        bottom.add(engineInfo, BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridLayout(0, 2, 6, 6));
        JButton newWhite = new JButton("New (White)");
        newWhite.addActionListener(e -> newGame(Piece.WHITE));
        JButton newBlack = new JButton("New (Black)");
        newBlack.addActionListener(e -> newGame(Piece.BLACK));
        JButton undo = new JButton("Undo");
        undo.addActionListener(e -> undo());
        JButton hint = new JButton("Hint");
        hint.addActionListener(e -> hint());
        JButton flip = new JButton("Flip board");
        flip.addActionListener(e -> boardPanel.setFlipped(!boardPanel.isFlipped()));
        controls.add(newWhite);
        controls.add(newBlack);
        controls.add(undo);
        controls.add(hint);
        controls.add(flip);
        controls.add(difficultyBox);
        bottom.add(controls, BorderLayout.SOUTH);
        side.add(bottom, BorderLayout.SOUTH);
        return side;
    }

    private JMenuBar buildMenuBar() {
        JMenuBar bar = new JMenuBar();
        JMenu game = new JMenu("Game");
        game.add(menuItem("New game as White", KeyEvent.VK_N, () -> newGame(Piece.WHITE)));
        game.add(menuItem("New game as Black", KeyEvent.VK_B, () -> newGame(Piece.BLACK)));
        game.add(menuItem("Load position (FEN)...", KeyEvent.VK_L, this::loadFen));
        game.add(menuItem("Copy position (FEN)", KeyEvent.VK_C, () -> Toolkit.getDefaultToolkit()
                .getSystemClipboard().setContents(new StringSelection(board.toFen()), null)));
        game.addSeparator();
        game.add(menuItem("Undo move", KeyEvent.VK_Z, this::undo));
        game.add(menuItem("Hint", KeyEvent.VK_H, this::hint));
        game.add(menuItem("Flip board", KeyEvent.VK_F, () -> boardPanel.setFlipped(!boardPanel.isFlipped())));
        bar.add(game);

        JMenu level = new JMenu("Difficulty");
        ButtonGroup group = new ButtonGroup();
        for (Difficulty d : Difficulty.values()) {
            JRadioButtonMenuItem item = new JRadioButtonMenuItem(d.label, d == Difficulty.MEDIUM);
            item.addActionListener(e -> difficultyBox.setSelectedItem(d));
            difficultyBox.addActionListener(e -> item.setSelected(difficultyBox.getSelectedItem() == d));
            group.add(item);
            level.add(item);
        }
        bar.add(level);
        return bar;
    }

    private static JMenuItem menuItem(String text, int key, Runnable action) {
        JMenuItem item = new JMenuItem(text);
        item.setAccelerator(KeyStroke.getKeyStroke(key, Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx()));
        item.addActionListener(e -> action.run());
        return item;
    }

    // --------------------------------------------------------------- game

    private void newGame(int color) {
        startFrom(new Board(), color);
    }

    private void startFrom(Board start, int color) {
        cancelThinking();
        engine.reset();
        board = start;
        firstMoveNumber = start.fullmoveNumber();
        firstMover = start.sideToMove();
        sanMoves.clear();
        humanColor = color;
        boardPanel.setFlipped(color == Piece.BLACK);
        engineInfo.setText("");
        refresh();
        maybeStartAi();
    }

    private void loadFen() {
        String fen = JOptionPane.showInputDialog(this, "Paste a FEN position:", board.toFen());
        if (fen == null || fen.isBlank()) {
            return;
        }
        try {
            Board loaded = new Board(fen);
            startFrom(loaded, loaded.sideToMove());
        } catch (RuntimeException ex) {
            JOptionPane.showMessageDialog(this, "That is not a valid FEN:\n" + ex.getMessage());
        }
    }

    private void humanMoved(Move move) {
        if (thinking || board.sideToMove() != humanColor || board.status().isOver()) {
            return;
        }
        play(move);
        maybeStartAi();
    }

    private void play(Move move) {
        sanMoves.add(Notation.toSan(board, move));
        board.makeMove(move);
        refresh();
    }

    private void undo() {
        cancelThinking();
        // Take back moves until it is the human's turn again (normally 2 plies).
        do {
            if (board.ply() == 0) {
                break;
            }
            board.unmakeMove();
            sanMoves.remove(sanMoves.size() - 1);
        } while (board.sideToMove() != humanColor);
        refresh();
        maybeStartAi();
    }

    private void hint() {
        if (thinking || board.status().isOver() || board.sideToMove() != humanColor) {
            return;
        }
        SearchResult r = new Engine().searchForTime(board, 700);
        engineInfo.setText("Hint: " + Notation.toSan(board, r.bestMove()) + "  (" + r.scoreText() + ")");
    }

    private void maybeStartAi() {
        if (board.status().isOver() || board.sideToMove() == humanColor) {
            return;
        }
        Difficulty difficulty = (Difficulty) difficultyBox.getSelectedItem();
        Board snapshot = new Board(board);
        int myGeneration = ++generation;
        thinking = true;
        boardPanel.setInteractive(false);
        refresh();

        new SwingWorker<SearchResult, SearchResult>() {
            @Override
            protected SearchResult doInBackground() {
                return engine.search(snapshot, difficulty.maxDepth, difficulty.millis, this::publish);
            }

            @Override
            protected void process(List<SearchResult> chunks) {
                if (myGeneration == generation) {
                    showThinking(snapshot, chunks.get(chunks.size() - 1));
                }
            }

            @Override
            protected void done() {
                if (myGeneration != generation) {
                    return; // the game changed while the AI was thinking
                }
                thinking = false;
                boardPanel.setInteractive(true);
                try {
                    SearchResult result = get();
                    showThinking(snapshot, result);
                    if (result.bestMove() != null) {
                        play(result.bestMove());
                    }
                } catch (InterruptedException | ExecutionException ex) {
                    engineInfo.setText("AI error: " + ex.getMessage());
                }
                refresh();
            }
        }.execute();
    }

    private void cancelThinking() {
        generation++;
        engine.stop();
        thinking = false;
        boardPanel.setInteractive(true);
    }

    // ------------------------------------------------------------ display

    /** Shows the AI's search progress. The evaluation is from White's point of view, like most chess software. */
    private void showThinking(Board position, SearchResult r) {
        boolean whiteToMove = position.sideToMove() == Piece.WHITE;
        String score;
        if (r.mateIn() != 0) {
            boolean whiteMates = (r.mateIn() > 0) == whiteToMove;
            score = (whiteMates ? "White" : "Black") + " mates in " + Math.abs(r.mateIn());
        } else {
            score = String.format("%+.2f", (whiteToMove ? r.score() : -r.score()) / 100.0);
        }
        engineInfo.setText(String.format("Depth: %d%nPositions: %,d (%,d/sec)%nEval: %s%nPlan: %s",
                r.depth(), r.nodes(), r.nodesPerSecond(), score, line(position, r.principalVariation())));
    }

    private static String line(Board position, List<Move> pv) {
        Board b = new Board(position);
        StringBuilder sb = new StringBuilder();
        for (Move m : pv) {
            sb.append(Notation.toSan(b, m)).append(' ');
            b.makeMove(m);
        }
        return sb.toString().trim();
    }

    private void refresh() {
        boardPanel.setBoard(board);
        boardPanel.setInteractive(!thinking);

        StringBuilder sb = new StringBuilder();
        int number = firstMoveNumber;
        for (int i = 0; i < sanMoves.size(); i++) {
            boolean whiteMove = (firstMover + i) % 2 == Piece.WHITE;
            if (whiteMove) {
                sb.append(number).append(". ");
            } else if (i == 0) {
                sb.append(number).append("... ");
            }
            sb.append(sanMoves.get(i)).append(' ');
            if (!whiteMove) {
                number++;
                sb.append('\n');
            }
        }
        moveList.setText(sb.toString());

        GameStatus status = board.status();
        String text;
        if (status == GameStatus.CHECKMATE) {
            String winner = Piece.colorName(1 - board.sideToMove());
            text = "Checkmate! " + winner + " wins.";
        } else if (status.isOver()) {
            text = status.description() + ".";
        } else if (thinking) {
            text = "AI is thinking...";
        } else {
            text = "Your move (" + Piece.colorName(humanColor) + ")" + (board.inCheck() ? " - check!" : "");
        }
        statusLabel.setText(text);
    }
}
