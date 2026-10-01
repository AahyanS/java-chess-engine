package chess.uci;

import chess.ai.Engine;
import chess.ai.SearchResult;
import chess.core.Board;
import chess.core.Move;
import chess.core.Notation;
import chess.core.Piece;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.stream.Collectors;

/**
 * The Universal Chess Interface (UCI) is the text protocol real chess programs use to
 * talk to engines. Supporting it means this engine can be loaded into free chess GUIs
 * such as Arena, Cute Chess or En Croissant and play against other engines.
 */
public final class UciProtocol {
    private final BufferedReader in;
    private final PrintStream out;
    private final Engine engine = new Engine();
    private Board board = new Board();
    private Thread searchThread;

    public UciProtocol(BufferedReader in, PrintStream out) {
        this.in = in;
        this.out = out;
    }

    public static void run() throws IOException {
        new UciProtocol(new BufferedReader(new InputStreamReader(System.in)), System.out).loop();
    }

    public void loop() throws IOException {
        String line;
        while ((line = in.readLine()) != null) {
            String[] tokens = line.trim().split("\\s+");
            switch (tokens[0]) {
                case "uci" -> {
                    send("id name Java Chess Engine");
                    send("id author Aahyan");
                    send("uciok");
                }
                case "isready" -> send("readyok");
                case "ucinewgame" -> {
                    stopSearch();
                    engine.reset();
                    board = new Board();
                }
                case "position" -> {
                    stopSearch();
                    setPosition(tokens);
                }
                case "go" -> go(tokens);
                case "stop" -> stopSearch();
                case "d" -> send(board.toDiagram() + "Fen: " + board.toFen());
                case "quit" -> {
                    stopSearch();
                    return;
                }
                default -> {
                    // Unknown commands are ignored, as the protocol requires.
                }
            }
        }
        stopSearch();
    }

    private void setPosition(String[] tokens) {
        int i = 1;
        if (tokens.length > 1 && tokens[1].equals("startpos")) {
            board = new Board();
            i = 2;
        } else if (tokens.length > 1 && tokens[1].equals("fen")) {
            StringBuilder fen = new StringBuilder();
            i = 2;
            while (i < tokens.length && !tokens[i].equals("moves")) {
                fen.append(tokens[i++]).append(' ');
            }
            board = new Board(fen.toString());
        }
        if (i < tokens.length && tokens[i].equals("moves")) {
            for (i++; i < tokens.length; i++) {
                Move m = Notation.parse(board, tokens[i]);
                if (m == null) {
                    break;
                }
                board.makeMove(m);
            }
        }
    }

    private void go(String[] tokens) {
        stopSearch();
        long wtime = -1, btime = -1, winc = 0, binc = 0, movetime = -1;
        int depth = 64;
        int movesToGo = 30;
        boolean infinite = false;
        for (int i = 1; i < tokens.length; i++) {
            String value = i + 1 < tokens.length ? tokens[i + 1] : "0";
            switch (tokens[i]) {
                case "wtime" -> wtime = Long.parseLong(value);
                case "btime" -> btime = Long.parseLong(value);
                case "winc" -> winc = Long.parseLong(value);
                case "binc" -> binc = Long.parseLong(value);
                case "movetime" -> movetime = Long.parseLong(value);
                case "movestogo" -> movesToGo = Math.max(1, Integer.parseInt(value));
                case "depth" -> depth = Integer.parseInt(value);
                case "infinite" -> infinite = true;
                default -> {
                    continue;
                }
            }
            i++;
        }
        long budget;
        long clock = board.sideToMove() == Piece.WHITE ? wtime : btime;
        long inc = board.sideToMove() == Piece.WHITE ? winc : binc;
        if (movetime > 0) {
            budget = movetime;
        } else if (clock > 0) {
            // Spend a fair share of the remaining time, never risking flagging.
            budget = Math.max(10, Math.min(clock / movesToGo + inc * 3 / 4, clock / 3));
        } else if (infinite || depth < 64) {
            budget = Long.MAX_VALUE / 4;
        } else {
            budget = 2000;
        }

        Board position = new Board(board);
        int maxDepth = depth;
        long millis = budget;
        searchThread = new Thread(() -> {
            SearchResult r = engine.search(position, maxDepth, millis, this::sendInfo);
            send("bestmove " + (r == null || r.bestMove() == null ? "0000" : r.bestMove().toUci()));
        }, "search");
        searchThread.start();
    }

    private void sendInfo(SearchResult r) {
        String score = r.mateIn() != 0 ? "mate " + r.mateIn() : "cp " + r.score();
        String pv = r.principalVariation().stream().map(Move::toUci).collect(Collectors.joining(" "));
        send("info depth " + r.depth() + " score " + score + " nodes " + r.nodes() + " nps " + r.nodesPerSecond()
                + " time " + r.millis() + " pv " + pv);
    }

    private void stopSearch() {
        if (searchThread != null) {
            engine.stop();
            try {
                searchThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            searchThread = null;
        }
    }

    private synchronized void send(String message) {
        out.println(message);
        out.flush();
    }
}
