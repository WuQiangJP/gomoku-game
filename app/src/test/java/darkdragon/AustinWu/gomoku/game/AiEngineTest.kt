package darkdragon.AustinWu.gomoku.game

import darkdragon.AustinWu.gomoku.engine.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AI エンジンのファサード層の単体テスト。
 */
class AiEngineTest {

    private val size = 15

    private fun at(row: Int, col: Int) = row * size + col

    @Test
    fun `中級は決め手を打てる`() {
        val cells = IntArray(size * size)
        for (col in 0 until 4) {
            cells[at(7, col)] = Board.BLACK
        }
        val engine = AiEngine(size)
        val move = engine.bestMove(cells, Board.BLACK, Difficulty.NORMAL)
        assertEquals(at(7, 4), move)
    }

    @Test
    fun `すべての難易度で合法手を返す`() {
        val cells = IntArray(size * size)
        cells[at(7, 7)] = Board.BLACK
        cells[at(8, 8)] = Board.WHITE
        for (difficulty in Difficulty.entries) {
            val engine = AiEngine(size)
            val move = engine.bestMove(cells, Board.WHITE, difficulty)
            assertTrue("$difficulty は合法手を返すべき", move in cells.indices)
            assertEquals("$difficulty は既に石がある点に打たない", Board.EMPTY, cells[move])
        }
    }

    @Test
    fun `難易度は名前から復元できる`() {
        assertEquals(Difficulty.HARD, Difficulty.fromName("HARD"))
        assertEquals(Difficulty.NORMAL, Difficulty.fromName("存在しない"))
    }
}
