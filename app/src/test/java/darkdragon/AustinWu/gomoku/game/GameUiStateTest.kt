package darkdragon.AustinWu.gomoku.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

/**
 * 画面状態の等値性規約：StateFlow は equals で重複排除するため、等しい状態は等しいと判定されねばならない。
 */
class GameUiStateTest {

    @Test
    fun `内容が同じ状態は等しくハッシュも一致する`() {
        assertEquals(GameUiState(), GameUiState())
        assertEquals(GameUiState().hashCode(), GameUiState().hashCode())
    }

    @Test
    fun `盤面が異なれば状態も異なる`() {
        val board = IntArray(225)
        board[112] = 1
        assertNotEquals(GameUiState(), GameUiState(board = board))
    }

    @Test
    fun `決め手の線は内容で比較する`() {
        val a = GameUiState(winningLine = intArrayOf(100, 101, 102, 103, 104))
        val b = GameUiState(winningLine = intArrayOf(100, 101, 102, 103, 104))
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `null の決め手と空の決め手は等しくない`() {
        assertNotEquals(GameUiState(winningLine = null), GameUiState(winningLine = IntArray(0)))
    }
}
