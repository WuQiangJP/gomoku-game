package darkdragon.AustinWu.gomoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 評価関数の単体テスト：主要な石の形の点数関係を検証する。
 */
class EvaluatorTest {

    private val table = LineTable(15)

    private fun evaluatorWith(cells: IntArray): Evaluator {
        val evaluator = Evaluator(table)
        evaluator.reset(cells)
        return evaluator
    }

    private fun cells(vararg stones: Pair<Int, Int>): IntArray {
        val cells = IntArray(225)
        for ((row, col) in stones) {
            cells[row * 15 + col] = Board.BLACK
        }
        return cells
    }

    @Test
    fun `五連の点数は達四より高い`() {
        val five = evaluatorWith(cells(7 to 0, 7 to 1, 7 to 2, 7 to 3, 7 to 4)).evaluate(Board.BLACK)
        val openFour = evaluatorWith(cells(7 to 5, 7 to 6, 7 to 7, 7 to 8)).evaluate(Board.BLACK)
        assertTrue("五連($five) は達四($openFour) より高いべき", five > openFour)
    }

    @Test
    fun `達四の点数は止められた四より高い`() {
        val openFour = evaluatorWith(cells(7 to 5, 7 to 6, 7 to 7, 7 to 8)).evaluate(Board.BLACK)
        val blocked = IntArray(225).also {
            for (col in 5..8) it[7 * 15 + col] = Board.BLACK
            it[7 * 15 + 9] = Board.WHITE
        }
        val blockedScore = evaluatorWith(blocked).evaluate(Board.BLACK)
        assertTrue("達四($openFour) は止められた四($blockedScore) より高いべき", openFour > blockedScore)
    }

    @Test
    fun `活三の点数は眠三より高い`() {
        val openThree = evaluatorWith(cells(7 to 6, 7 to 7, 7 to 8)).evaluate(Board.BLACK)
        val sleepThree = IntArray(225).also {
            for (col in 6..8) it[7 * 15 + col] = Board.BLACK
            it[7 * 15 + 9] = Board.WHITE
        }
        val sleepScore = evaluatorWith(sleepThree).evaluate(Board.BLACK)
        assertTrue("活三($openThree) は眠三($sleepScore) より高いべき", openThree > sleepScore)
    }

    @Test
    fun `対称な局面では両者の点数が等しい`() {
        val cells = IntArray(225)
        cells[7 * 15 + 3] = Board.BLACK
        cells[7 * 15 + 4] = Board.WHITE
        val evaluator = evaluatorWith(cells)
        assertEquals(0, evaluator.evaluate(Board.BLACK))
    }

    @Test
    fun `着手で点数が更新され取り消せる`() {
        val cells = IntArray(225)
        val evaluator = evaluatorWith(cells)
        val before = evaluator.evaluate(Board.BLACK)
        cells[7 * 15 + 7] = Board.BLACK
        evaluator.onPlay(7 * 15 + 7, Board.BLACK)
        assertTrue(evaluator.evaluate(Board.BLACK) > before)
        evaluator.onUnplay(7 * 15 + 7)
        assertEquals(before, evaluator.evaluate(Board.BLACK))
    }
}
