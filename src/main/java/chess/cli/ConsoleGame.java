package chess.cli;

import chess.ai.Engine;
import chess.ai.SearchResult;
import chess.core.Board;
import chess.core.GameStatus;
import chess.core.Move;
import chess.core.Notation;
import chess.core.Piece;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.stream.Collectors;

/** Play against the AI in a terminal by typing moves like {@code e4}, {@code Nf3} or {@code e2e4}. */
public final class ConsoleGame {
    private final BufferedReader in;
    private final PrintStream out;
    private final Engine engine = new Engine();
    private Board board = new Board();
    private int humanColor = Piece.WHITE;
    private long thinkMillis = 1000;

    public ConsoleGame(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public static void run() throws IOException {
        new ConsoleGame(new BufferedReader(new InputStreamReader(System.in)), System.out).loop();
    }

    public void loop() throws IOException {
        out.println("Java Chess Engine - console mode. Type 'help' for commands.");
        printBoard();
        while (true) {
            GameStatus status = board.status();
            if (status.isOver()) {
                announce(status);
                out.println("Type 'new' to play again or 'quit' to exit.");
            } else if (board.sideToMove() != humanColor) {
                aiMove();
                continue;
            }
            out.print(Piece.colorName(board.sideToMove()) + " to move> ");
            out.flush();
            String line = in.readLine();
            if (line == null) {
                return;
            }
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] words = line.split("\\s+");
            switch (words[0].toLowerCase()) {
                case "quit", "exit" -> {
                    return;
                }
                case "help" -> printHelp();
                case "board" -> printBoard();
                case "moves" -> out.println(board.legalMoves().stream()
                        .map(m -> Notation.toSan(board, m)).sorted().collect(Collectors.joining(" ")));
                case "fen" -> out.println(board.toFen());
                case "new" -> {
                    humanColor = words.length > 1 && words[1].startsWith("b") ? Piece.BLACK : Piece.WHITE;
                    board = new Board();
                    engine.reset();
                    printBoard();
                }
                case "undo" -> {
                    if (board.ply() >= 2) {
                        board.unmakeMove();
                        board.unmakeMove();
                    }
                    printBoard();
                }
                case "time" -> {
                    if (words.length > 1) {
                        thinkMillis = (long) (Double.parseDouble(words[1]) * 1000);
                    }
                    out.println("AI thinks for " + thinkMillis / 1000.0 + " seconds per move.");
                }
                case "hint" -> {
                    SearchResult r = new Engine().searchForTime(board, 1000);
                    out.println("Hint: " + Notation.toSan(board, r.bestMove()));
                }
                default -> {
                    if (status.isOver()) {
                        out.println("The game is over. Type 'new' to play again.");
                        continue;
                    }
                    Move move = Notation.parse(board, line);
                    if (move == null) {
                        out.println("Not a legal move: " + line + " (type 'moves' to list legal moves)");
                    } else {
                        out.println("You played " + Notation.toSan(board, move));
                        board.makeMove(move);
                        printBoard();
                    }
                }
            }
        }
    }

    private void aiMove() {
        out.println("AI is thinking...");
        SearchResult r = engine.searchForTime(board, thinkMillis);
        String san = Notation.toSan(board, r.bestMove());
        out.printf("AI plays %s   (depth %d, %,d positions, eval %s)%n", san, r.depth(), r.nodes(), r.scoreText());
        board.makeMove(r.bestMove());
        printBoard();
    }

    private void announce(GameStatus status) {
        if (status == GameStatus.CHECKMATE) {
            out.println("Checkmate! " + Piece.colorName(1 - board.sideToMove()) + " wins.");
        } else {
            out.println(status.description() + ".");
        }
    }

    private void printBoard() {
        out.println();
        out.print(board.toDiagram());
        if (board.inCheck() && !board.status().isOver()) {
            out.println("Check!");
        }
        out.println();
    }

    private void printHelp() {
        out.println("""
                Enter a move in algebraic notation (e4, Nf3, O-O, exd5, e8=Q) or coordinates (e2e4).
                Commands:
                  moves        list legal moves
                  hint         ask the engine for a suggestion
                  undo         take back your last move
                  new [black]  start a new game (optionally as Black)
                  time <sec>   set how long the AI thinks
                  fen          print the position in FEN
                  board        print the board
                  quit         exit""");
    }
}
