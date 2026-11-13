package darkdragon.AustinWu.gomoku.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 盤面と勝敗判定の単体テスト。
 */
class BoardTest {

    private fun index(row: Int, col: Int, size: Int = 15) = row * size + col

    @Test
    fun `横の五連を正しく判定できる`() {
        val board = Board(15)
        for (col in 0 until 5) {
            board.play(index(7, col), Board.BLACK)
        }
        val line = board.winningCells(index(7, 4), Board.BLACK)
        assertNotNull(line)
        assertEquals(5, line!!.size)
        assertTrue(board.hasFive(Board.BLACK))
        assertFalse(board.hasFive(Board.WHITE))
    }

    @Test
    fun `縦の五連を正しく判定できる`() {
        val board = Board(15)
        for (row in 0 until 5) {
            board.play(index(row, 3), Board.WHITE)
        }
        assertNotNull(board.winningCells(index(4, 3), Board.WHITE))
    }

    @Test
    fun `斜めの五連を正しく判定できる`() {
        val board = Board(15)
        for (i in 0 until 5) {
            board.play(index(i, i), Board.BLACK)
        }
        assertNotNull(board.winningCells(index(4, 4), Board.BLACK))
    }

    @Test
    fun `反対向きの斜め五連を正しく判定できる`() {
        val board = Board(15)
        for (i in 0 until 5) {
            board.play(index(i, 14 - i), Board.WHITE)
        }
        assertNotNull(board.winningCells(index(4, 10), Board.WHITE))
    }

    @Test
    fun `四連は勝ちにならない`() {
        val board = Board(15)
        for (col in 0 until 4) {
            board.play(index(7, col), Board.BLACK)
        }
        assertNull(board.winningCells(index(7, 3), Board.BLACK))
        assertFalse(board.hasFive(Board.BLACK))
    }

    @Test
    fun `着手を取り消すと局面が元に戻る`() {
        val board = Board(15)
        val hashBefore = board.hash
        board.play(index(7, 7), Board.BLACK)
        assertFalse(board.hash == hashBefore)
        board.unplay(index(7, 7), Board.BLACK)
        assertEquals(hashBefore, board.hash)
        assertTrue(board.isEmpty(index(7, 7)))
    }

    @Test
    fun `同じ場所への二重着手は拒否される`() {
        val board = Board(15)
        board.play(index(7, 7), Board.BLACK)
        var failed = false
        try {
            board.play(index(7, 7), Board.WHITE)
        } catch (e: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun `setCells はハッシュを再計算する`() {
        val cells = IntArray(225)
        cells[index(3, 3)] = Board.BLACK
        cells[index(4, 4)] = Board.WHITE

        val restored = Board(15)
        restored.setCells(cells)

        // 1 手ずつ打った場合とハッシュが一致しなければならない
        val played = Board(15)
        played.play(index(3, 3), Board.BLACK)
        played.play(index(4, 4), Board.WHITE)
        assertEquals(played.hash, restored.hash)

        // 別の局面では別のハッシュにならなければならない
        val otherCells = IntArray(225)
        otherCells[index(5, 5)] = Board.BLACK
        val other = Board(15)
        other.setCells(otherCells)
        assertNotEquals(restored.hash, other.hash)
    }
}
