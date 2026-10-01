package chess.ai;

import chess.core.Board;
import chess.core.Move;
import chess.core.MoveGenerator;
import chess.core.Piece;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The chess AI. Given a position, it looks ahead through possible moves and replies
 * and picks the move that leads to the best position it can force.
 *
 * <p>Techniques used (each is explained in the README):
 * <ul>
 *   <li><b>Minimax with alpha-beta pruning</b> (in "negamax" form): assume both sides
 *       play their best, and skip branches that cannot change the decision.</li>
 *   <li><b>Iterative deepening</b>: search 1 move deep, then 2, then 3, ... until time
 *       runs out, always keeping the best move from the last finished depth.</li>
 *   <li><b>Quiescence search</b>: at the end of the planned depth, keep following
 *       captures so the engine never stops looking in the middle of a trade.</li>
 *   <li><b>Transposition table</b>: remember positions already searched.</li>
 *   <li><b>Move ordering</b>: try the most promising moves first (previous best move,
 *       good captures, "killer" moves) so alpha-beta can prune more.</li>
 * </ul>
 */
public final class Engine {
    public static final int MATE = 100_000;
    private static final int INFINITY = 1_000_000;
    private static final int MAX_PLY = 128;
    /** Scores this close to MATE mean a forced mate was found. */
    private static final int MATE_THRESHOLD = MATE - MAX_PLY;

    private final TranspositionTable table = new TranspositionTable(20);
    private final Move[][] killers = new Move[MAX_PLY][2];
    private final int[][] historyScores = new int[64][64];

    private Board board;
    private long nodes;
    private long startTime;
    private long deadline;
    private volatile boolean stopRequested;
    private boolean outOfTime;

    /** Asks a running search (on another thread) to stop as soon as possible. */
    public void stop() {
        stopRequested = true;
    }

    /** Forget everything learned from previous searches (call between games). */
    public void reset() {
        table.clear();
    }

    /** Search for at most {@code millis} milliseconds. */
    public SearchResult searchForTime(Board position, long millis) {
        return search(position, MAX_PLY - 1, millis, null);
    }

    /** Search exactly {@code depth} moves deep (plus captures), with no time limit. */
    public SearchResult searchToDepth(Board position, int depth) {
        return search(position, depth, Long.MAX_VALUE / 4, null);
    }

    /**
     * Runs iterative deepening up to {@code maxDepth} or until {@code millis} elapse.
     * {@code onDepthDone}, if not null, is told the result after each completed depth.
     * The given board is not modified.
     */
    public SearchResult search(Board position, int maxDepth, long millis, Consumer<SearchResult> onDepthDone) {
        board = new Board(position);
        nodes = 0;
        startTime = System.currentTimeMillis();
        deadline = startTime + Math.max(1, millis);
        stopRequested = false;
        outOfTime = false;
        for (Move[] k : killers) {
            k[0] = k[1] = null;
        }
        for (int[] h : historyScores) {
            java.util.Arrays.fill(h, 0);
        }

        List<Move> rootMoves = board.legalMoves();
        if (rootMoves.isEmpty()) {
            return new SearchResult(null, board.inCheck() ? -MATE : 0, 0, 0, 0, 0, List.of());
        }

        SearchResult best = null;
        for (int depth = 1; depth <= maxDepth; depth++) {
            int bestScore = -INFINITY;
            Move bestMove = null;
            orderRootMoves(rootMoves, best == null ? null : best.bestMove());
            for (Move m : rootMoves) {
                board.makeMove(m);
                int score = -negamax(depth - 1, -INFINITY, -bestScore, 1);
                board.unmakeMove();
                if (outOfTime) {
                    break;
                }
                if (score > bestScore) {
                    bestScore = score;
                    bestMove = m;
                }
            }
            if (outOfTime && best != null) {
                // The previous best move is always searched first, so if another move
                // beat it before time ran out, that move is a genuine improvement.
                if (bestMove != null && !bestMove.equals(rootMoves.get(0))) {
                    best = makeResult(bestMove, bestScore, depth);
                }
                break;
            }
            if (bestMove == null) {
                bestMove = rootMoves.get(0);
            }
            table.store(board.hash(), depth, bestScore, TranspositionTable.EXACT, bestMove);
            best = makeResult(bestMove, bestScore, depth);
            if (onDepthDone != null) {
                onDepthDone.accept(best);
            }
            if (outOfTime || Math.abs(bestScore) >= MATE_THRESHOLD && depth > 1) {
                break; // a forced mate is certain; deeper search will not change it
            }
            // Starting a new depth that we almost certainly cannot finish wastes time.
            long elapsed = System.currentTimeMillis() - startTime;
            if (elapsed * 2 > deadline - startTime) {
                break;
            }
        }
        return best;
    }

    private SearchResult makeResult(Move bestMove, int score, int depth) {
        int mateIn = 0;
        if (score >= MATE_THRESHOLD) {
            mateIn = (MATE - score + 1) / 2;
        } else if (score <= -MATE_THRESHOLD) {
            mateIn = -(MATE + score) / 2;
        }
        long elapsed = System.currentTimeMillis() - startTime;
        return new SearchResult(bestMove, score, mateIn, depth, nodes, elapsed, principalVariation(bestMove, depth));
    }

    private void orderRootMoves(List<Move> moves, Move previousBest) {
        if (previousBest != null && moves.remove(previousBest)) {
            moves.add(0, previousBest);
        } else {
            moves.sort((a, b) -> Integer.compare(orderScore(b, null, 0), orderScore(a, null, 0)));
        }
    }

    /**
     * Negamax alpha-beta: returns the score of the position for the side to move.
     * Alpha is the best score we are already guaranteed elsewhere, beta is the most the
     * opponent will allow; any line outside that window can be skipped.
     */
    private int negamax(int depth, int alpha, int beta, int ply) {
        if (timeUp()) {
            return 0;
        }
        if (board.halfmoveClock() >= 100 || board.isRepetition(1) || board.isInsufficientMaterial()) {
            return 0;
        }
        boolean inCheck = board.inCheck();
        if (inCheck) {
            depth++; // check extension: never stop searching while in check
        }
        if (depth <= 0 || ply >= MAX_PLY - 1) {
            return quiescence(alpha, beta, ply);
        }
        nodes++;

        TranspositionTable.Entry entry = table.probe(board.hash());
        Move hashMove = null;
        if (entry != null) {
            hashMove = entry.bestMove();
            if (entry.depth() >= depth) {
                int score = fromTable(entry.score(), ply);
                if (entry.flag() == TranspositionTable.EXACT
                        || entry.flag() == TranspositionTable.LOWER_BOUND && score >= beta
                        || entry.flag() == TranspositionTable.UPPER_BOUND && score <= alpha) {
                    return score;
                }
            }
        }

        List<Move> moves = new ArrayList<>(48);
        MoveGenerator.generate(board, moves, false);
        int[] scores = new int[moves.size()];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = orderScore(moves.get(i), hashMove, ply);
        }

        int originalAlpha = alpha;
        int bestScore = -INFINITY;
        Move bestMove = null;
        int legalMoves = 0;
        for (int i = 0; i < moves.size(); i++) {
            Move m = pickNext(moves, scores, i);
            boolean quiet = isQuiet(m);
            board.makeMove(m);
            if (board.leftKingInCheck()) {
                board.unmakeMove();
                continue;
            }
            legalMoves++;
            int score = -negamax(depth - 1, -beta, -alpha, ply + 1);
            board.unmakeMove();
            if (outOfTime) {
                return 0;
            }
            if (score > bestScore) {
                bestScore = score;
                bestMove = m;
            }
            if (score > alpha) {
                alpha = score;
            }
            if (alpha >= beta) {
                if (quiet) {
                    rememberKiller(m, ply);
                    historyScores[m.from()][m.to()] += depth * depth;
                }
                break;
            }
        }

        if (legalMoves == 0) {
            return inCheck ? -MATE + ply : 0; // checkmate (prefer faster mates) or stalemate
        }
        int flag = bestScore <= originalAlpha ? TranspositionTable.UPPER_BOUND
                : bestScore >= beta ? TranspositionTable.LOWER_BOUND
                : TranspositionTable.EXACT;
        table.store(board.hash(), depth, toTable(bestScore, ply), flag, bestMove);
        return bestScore;
    }

    /**
     * Searches only captures and promotions until the position is "quiet", so the
     * evaluation is never taken in the middle of an exchange. The side to move may
     * also "stand pat" (decline to capture) if its position is already good enough.
     */
    private int quiescence(int alpha, int beta, int ply) {
        if (timeUp()) {
            return 0;
        }
        nodes++;
        boolean inCheck = board.inCheck();
        if (!inCheck) {
            int standPat = Evaluator.evaluate(board);
            if (standPat >= beta || ply >= MAX_PLY - 1) {
                return standPat;
            }
            alpha = Math.max(alpha, standPat);
        }

        // In check every move must be considered (there is no "doing nothing").
        List<Move> moves = new ArrayList<>(inCheck ? 48 : 16);
        MoveGenerator.generate(board, moves, !inCheck);
        int[] scores = new int[moves.size()];
        for (int i = 0; i < scores.length; i++) {
            scores[i] = orderScore(moves.get(i), null, ply);
        }
        int legalMoves = 0;
        int bestScore = inCheck ? -INFINITY : alpha;
        for (int i = 0; i < moves.size(); i++) {
            Move m = pickNext(moves, scores, i);
            board.makeMove(m);
            if (board.leftKingInCheck()) {
                board.unmakeMove();
                continue;
            }
            legalMoves++;
            int score = -quiescence(-beta, -alpha, ply + 1);
            board.unmakeMove();
            if (outOfTime) {
                return 0;
            }
            if (score > bestScore) {
                bestScore = score;
            }
            if (score > alpha) {
                alpha = score;
                if (alpha >= beta) {
                    break;
                }
            }
        }
        if (inCheck && legalMoves == 0) {
            return -MATE + ply;
        }
        return bestScore;
    }

    // ------------------------------------------------------------ ordering

    /**
     * Higher scores are searched first. Captures use "MVV-LVA" (most valuable victim,
     * least valuable attacker): taking a queen with a pawn is tried before taking a pawn
     * with a queen.
     */
    private int orderScore(Move m, Move hashMove, int ply) {
        if (m.equals(hashMove)) {
            return 10_000_000;
        }
        int victim = m.isEnPassant() ? Piece.PAWN : Piece.type(board.pieceAt(m.to()));
        if (victim != Piece.EMPTY) {
            int attacker = Piece.type(board.pieceAt(m.from()));
            return 1_000_000 + Evaluator.PIECE_VALUES[victim] * 10 - Evaluator.PIECE_VALUES[attacker] / 10;
        }
        if (m.isPromotion()) {
            return 900_000 + Evaluator.PIECE_VALUES[m.promotion()];
        }
        if (m.equals(killers[ply][0])) {
            return 800_000;
        }
        if (m.equals(killers[ply][1])) {
            return 700_000;
        }
        return Math.min(historyScores[m.from()][m.to()], 600_000);
    }

    /** Selection-sort step: swap the best remaining move into position {@code i}. */
    private static Move pickNext(List<Move> moves, int[] scores, int i) {
        int best = i;
        for (int j = i + 1; j < scores.length; j++) {
            if (scores[j] > scores[best]) {
                best = j;
            }
        }
        if (best != i) {
            Move tmp = moves.get(i);
            moves.set(i, moves.get(best));
            moves.set(best, tmp);
            int s = scores[i];
            scores[i] = scores[best];
            scores[best] = s;
        }
        return moves.get(i);
    }

    private boolean isQuiet(Move m) {
        return board.pieceAt(m.to()) == Piece.EMPTY && !m.isEnPassant() && !m.isPromotion();
    }

    private void rememberKiller(Move m, int ply) {
        if (!m.equals(killers[ply][0])) {
            killers[ply][1] = killers[ply][0];
            killers[ply][0] = m;
        }
    }

    // ------------------------------------------------------------- helpers

    private boolean timeUp() {
        if (outOfTime) {
            return true;
        }
        if ((nodes & 1023) == 0 && (stopRequested || System.currentTimeMillis() >= deadline)) {
            outOfTime = true;
        }
        return outOfTime;
    }

    /** Mate scores are stored relative to the current node so they stay correct at any depth. */
    private static int toTable(int score, int ply) {
        if (score >= MATE_THRESHOLD) return score + ply;
        if (score <= -MATE_THRESHOLD) return score - ply;
        return score;
    }

    private static int fromTable(int score, int ply) {
        if (score >= MATE_THRESHOLD) return score - ply;
        if (score <= -MATE_THRESHOLD) return score + ply;
        return score;
    }

    /** Rebuilds the expected line of play by following best moves stored in the table. */
    private List<Move> principalVariation(Move first, int maxLength) {
        List<Move> line = new ArrayList<>();
        Move next = first;
        while (next != null && line.size() < maxLength && board.legalMoves().contains(next)) {
            line.add(next);
            board.makeMove(next);
            if (board.isRepetition(1)) {
                break;
            }
            TranspositionTable.Entry e = table.probe(board.hash());
            next = e == null ? null : e.bestMove();
        }
        for (int i = 0; i < line.size(); i++) {
            board.unmakeMove();
        }
        return line;
    }
}
