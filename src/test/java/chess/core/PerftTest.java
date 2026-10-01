package chess.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Perft ("performance test") counts every position reachable in N moves and compares
 * it with the numbers published on the Chess Programming Wiki
 * (https://www.chessprogramming.org/Perft_Results). These positions are designed to
 * hit every tricky rule: castling through check, en passant pins, promotions, and so on.
 * If any rule were implemented wrong, these counts would not match.
 */
class PerftTest {

    @ParameterizedTest(name = "{0} depth {2} = {3}")
    @CsvSource(delimiter = '|', value = {
        "start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 1 | 20",
        "start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 2 | 400",
        "start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 3 | 8902",
        "start position | rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1 | 4 | 197281",
        "Kiwipete       | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 1 | 48",
        "Kiwipete       | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 2 | 2039",
        "Kiwipete       | r3k2r/p1ppqpb1/bn2pnp1/3PN3/1p2P3/2N2Q1p/PPPBBPPP/R3K2R w KQkq - 0 1 | 3 | 97862",
        "position 3     | 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 4 | 43238",
        "position 3     | 8/2p5/3p4/KP5r/1R3p1k/8/4P1P1/8 w - - 0 1 | 5 | 674624",
        "position 4     | r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 3 | 9467",
        "position 4     | r3k2r/Pppp1ppp/1b3nbN/nP6/BBP1P3/q4N2/Pp1P2PP/R2Q1RK1 w kq - 0 1 | 4 | 422333",
        "position 5     | rnbq1k1r/pp1Pbppp/2p5/8/2B5/8/PPP1NnPP/RNBQK2R w KQ - 1 8 | 3 | 62379",
        "position 6     | r4rk1/1pp1qppp/p1np1n2/2b1p1B1/2B1P1b1/P1NP1N2/1PP1QPPP/R4RK1 w - - 0 10 | 3 | 89890",
    })
    void perftMatchesPublishedCounts(String name, String fen, int depth, long expected) {
        Board board = new Board(fen);
        assertEquals(expected, MoveGenerator.perft(board, depth));
        assertEquals(fen, board.toFen(), "board should be restored after perft");
    }
}
