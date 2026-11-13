package darkdragon.AustinWu.gomoku.game

import darkdragon.AustinWu.gomoku.engine.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 対局ルール層の単体テスト。
 */
class GameStateTest {

    private fun at(row: Int, col: Int, size: Int = 15) = row * size + col

    @Test
    fun `黒が先手で交互に打つ`() {
        val state = GameState(15)
        assertEquals(Board.BLACK, state.currentPlayer)
        assertTrue(state.play(at(7, 7)))
        assertEquals(Board.WHITE, state.currentPlayer)
        assertTrue(state.play(at(7, 8)))
        assertEquals(Board.BLACK, state.currentPlayer)
    }

    @Test
    fun `既に石がある場所には打てない`() {
        val state = GameState(15)
        assertTrue(state.play(at(7, 7)))
        assertFalse(state.play(at(7, 7)))
    }

    @Test
    fun `五連で対局が終わり決め手が記録される`() {
        val state = GameState(15)
        state.play(at(7, 0))
        state.play(at(8, 0))
        state.play(at(7, 1))
        state.play(at(8, 1))
        state.play(at(7, 2))
        state.play(at(8, 2))
        state.play(at(7, 3))
        state.play(at(8, 3))
        state.play(at(7, 4))
        assertEquals(GameState.Status.FINISHED, state.status)
        assertEquals(Board.BLACK, state.winner)
        assertNotNull(state.winningLine)
        assertEquals(5, state.winningLine!!.size)
    }

    @Test
    fun `対局終了後は打てない`() {
        val state = GameState(15)
        for (col in 0 until 5) {
            state.play(at(7, col))
            if (col < 4) state.play(at(8, col))
        }
        assertEquals(GameState.Status.FINISHED, state.status)
        assertFalse(state.play(at(0, 0)))
    }

    @Test
    fun `待ったで直前の一手の状態に戻る`() {
        val state = GameState(15)
        state.play(at(7, 7))
        state.play(at(7, 8))
        assertEquals(Board.BLACK, state.currentPlayer)
        assertTrue(state.undo())
        assertEquals(Board.WHITE, state.currentPlayer)
        assertEquals(1, state.moveCount())
    }

    @Test
    fun `やり直すと盤が空になる`() {
        val state = GameState(15)
        state.play(at(7, 7))
        state.reset()
        assertEquals(0, state.moveCount())
        assertEquals(GameState.Status.PLAYING, state.status)
        assertEquals(Board.EMPTY, state.colorAt(at(7, 7)))
    }

    @Test
    fun `手順から局面を復元できる`() {
        val state = GameState(15)
        state.play(at(7, 7))
        state.play(at(7, 8))
        state.play(at(6, 6))

        val restored = GameState(15)
        restored.restore(listOf(at(7, 7), at(7, 8), at(6, 6)), Board.BLACK)
        assertEquals(3, restored.moveCount())
        assertEquals(Board.WHITE, restored.currentPlayer)
        assertEquals(state.snapshot().toList(), restored.snapshot().toList())
    }

    @Test
    fun `盤が埋まり勝者がいなければ引き分け`() {
        val state = GameState(3)
        // 3x3 の盤は最大 9 手で、交互に打っても五連はできない
        val positions = listOf(0, 1, 2, 4, 3, 5, 7, 6, 8)
        for (position in positions) {
            state.play(position)
        }
        assertEquals(GameState.Status.DRAW, state.status)
        assertNull(state.winningLine)
    }
}
