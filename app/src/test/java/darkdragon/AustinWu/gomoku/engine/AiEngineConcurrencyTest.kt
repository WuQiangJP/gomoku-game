package darkdragon.AustinWu.gomoku.engine

import darkdragon.AustinWu.gomoku.game.AiEngine
import darkdragon.AustinWu.gomoku.game.Difficulty
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 並行実行の回帰：AI の思考中に画面がただちに対局をやり直したり待ったをしたりでき、
 * 2 つの探索が同時に走る。呼び出しごとに独立した状態でなければ盤面の衝突例外が出る。
 */
class AiEngineConcurrencyTest {

    @Test
    fun `同じ AiEngine への並行呼び出しで例外が出ない`() {
        val engine = AiEngine(15)
        val cells = IntArray(15 * 15)
        cells[7 * 15 + 7] = Board.BLACK
        cells[6 * 15 + 6] = Board.WHITE

        val errors = ConcurrentLinkedQueue<Throwable>()
        val threads = (0 until 4).map { worker ->
            Thread {
                for (round in 0 until 4) {
                    val side = if ((worker + round) % 2 == 0) Board.BLACK else Board.WHITE
                    try {
                        engine.bestMove(cells.copyOf(), side, Difficulty.EASY)
                    } catch (t: Throwable) {
                        errors.add(t)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join(120_000) }

        assertTrue(
            "並行探索で例外が出てはならない。実際の最初の例外: ${errors.peek()}",
            errors.isEmpty(),
        )
    }

    @Test
    fun `キャンセルコールバックで探索が速やかに合法手を返す`() {
        val searcher = Searcher(15)
        val cells = IntArray(15 * 15)
        for (col in 3 until 9) {
            cells[7 * 15 + col] = Board.BLACK
            cells[5 * 15 + col] = Board.WHITE
        }
        val started = System.nanoTime()
        // ただちにキャンセルする。探索は最初のチェックポイントで抜け、完了済みの
        // 浅い層の結果にフォールバックする
        val result = searcher.search(
            cells = cells,
            side = Board.BLACK,
            maxDepth = 8,
            timeLimitMs = 10_000,
            isCancelled = { true },
        )
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("キャンセル後も合法手が必要。実際: ${result.move}", result.move in cells.indices)
        assertTrue("キャンセル後はただちに戻るべき。実際 ${elapsedMs}ms", elapsedMs < 3_000)
    }
}
