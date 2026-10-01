package chess;

import chess.ai.Engine;
import chess.ai.SearchResult;
import chess.cli.ConsoleGame;
import chess.core.Board;
import chess.core.MoveGenerator;
import chess.core.Notation;
import chess.uci.UciProtocol;
import chess.ui.ChessWindow;
import java.awt.GraphicsEnvironment;

/**
 * Entry point.
 *
 * <pre>
 *   java -jar chess-engine.jar              open the game window
 *   java -jar chess-engine.jar --console    play in the terminal
 *   java -jar chess-engine.jar --uci        run as a UCI engine for chess GUIs
 *   java -jar chess-engine.jar --perft 5    count positions 5 moves deep (speed test)
 *   java -jar chess-engine.jar --analyze "&lt;FEN&gt;" [seconds]
 * </pre>
 */
public final class Main {
    private Main() {
    }

    public static void main(String[] args) throws Exception {
        String mode = args.length > 0 ? args[0] : "";
        switch (mode) {
            case "--console", "-c" -> ConsoleGame.run();
            case "--uci" -> UciProtocol.run();
            case "--perft" -> perft(args);
            case "--analyze" -> analyze(args);
            case "--help", "-h" -> usage();
            case "" -> {
                if (GraphicsEnvironment.isHeadless()) {
                    System.out.println("No display found, starting console mode.");
                    ConsoleGame.run();
                } else {
                    ChessWindow.launch();
                }
            }
            default -> {
                System.out.println("Unknown option: " + mode);
                usage();
            }
        }
    }

    private static void usage() {
        System.out.println("""
                Usage: java -jar chess-engine.jar [option]
                  (no option)            open the game window
                  --console              play in the terminal
                  --uci                  run as a UCI engine for chess GUIs
                  --perft <depth> [fen]  count positions <depth> moves deep and time it
                  --analyze <fen> [sec]  print the engine's best move for a position""");
    }

    private static void perft(String[] args) {
        int depth = args.length > 1 ? Integer.parseInt(args[1]) : 5;
        Board board = new Board(args.length > 2 ? args[2] : Board.START_FEN);
        long start = System.nanoTime();
        long nodes = MoveGenerator.perft(board, depth);
        double seconds = (System.nanoTime() - start) / 1e9;
        System.out.printf("perft(%d) = %,d positions in %.2f s (%,.0f positions/sec)%n",
                depth, nodes, seconds, nodes / seconds);
    }

    private static void analyze(String[] args) {
        Board board = new Board(args.length > 1 ? args[1] : Board.START_FEN);
        long millis = (long) ((args.length > 2 ? Double.parseDouble(args[2]) : 3) * 1000);
        System.out.print(board.toDiagram());
        SearchResult result = new Engine().search(board, 64, millis, r -> System.out.printf(
                "depth %2d  eval %-12s nodes %,12d  best %s%n", r.depth(), r.scoreText(), r.nodes(),
                Notation.toSan(board, r.bestMove())));
        if (result.bestMove() == null) {
            System.out.println("No legal moves: " + board.status().description());
        } else {
            System.out.println("Best move: " + Notation.toSan(board, result.bestMove()));
        }
    }
}
