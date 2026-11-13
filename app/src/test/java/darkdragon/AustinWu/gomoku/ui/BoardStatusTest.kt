package darkdragon.AustinWu.gomoku.ui

import darkdragon.AustinWu.gomoku.engine.Board
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 盤面のアクセシビリティ説明文の単体テスト。スクリーンリーダーと自動検証の双方が依存する。
 */
class BoardStatusTest {

    @Test
    fun `説明文に両者の石数が含まれる`() {
        val board = IntArray(225)
        board[0] = Board.BLACK
        board[1] = Board.WHITE
        board[2] = Board.BLACK
        assertEquals("盤面、黒石 2 枚、白石 1 枚", boardDescription(board, null))
    }

    @Test
    fun `空盤の説明文は 0 枚になる`() {
        assertEquals("盤面、黒石 0 枚、白石 0 枚", boardDescription(IntArray(225), null))
    }

    @Test
    fun `決着がついた場合は状態を末尾に付ける`() {
        val line = intArrayOf(100, 101, 102, 103, 104)
        val text = boardDescription(IntArray(225), line)
        assertTrue("決着済みと示すべき。実際: $text", text.endsWith("、決着がつきました"))
    }
}
