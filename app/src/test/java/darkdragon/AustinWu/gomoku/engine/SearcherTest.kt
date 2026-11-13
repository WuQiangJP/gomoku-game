package darkdragon.AustinWu.gomoku.engine

import darkdragon.AustinWu.gomoku.game.AiEngine
import darkdragon.AustinWu.gomoku.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 探索器の単体テスト：AI が勝てる、受けられる、常に合法手を返すことを検証する。
 */
class SearcherTest {

    private val size = 15

    private fun at(row: Int, col: Int) = row * size + col

    @Test
    fun `ただちに五連を作れる場合は決め手を選ぶ`() {
        val cells = IntArray(size * size)
        for (col in 0 until 4) {
            cells[at(7, col)] = Board.BLACK
        }
        val searcher = Searcher(size)
        val result = searcher.search(cells, Board.BLACK, maxDepth = 2, timeLimitMs = 3000)
        assertEquals(at(7, 4), result.move)
    }

    @Test
    fun `相手が次に五連を作る場合は必ず受ける`() {
        val cells = IntArray(size * size)
        for (col in 5 until 9) {
            cells[at(7, col)] = Board.WHITE
        }
        val searcher = Searcher(size)
        val result = searcher.search(cells, Board.BLACK, maxDepth = 4, timeLimitMs = 5000)
        assertTrue(
            "AI は白の五連成立点 (7,4) か (7,9) を止めるべき。実際: ${result.move}",
            result.move == at(7, 4) || result.move == at(7, 9),
        )
    }

    /**
     * 白が第 10 行に四連 (10,4)～(10,7) を持ち、(10,3) は黒石でふさがれているため、
     * 白の五連成立点は (10,8) の 1 点だけ。黒は第 6 行に別の三連を持ち四を作れる。
     * このとき黒はどの手を選んでも、まず (10,8) を止めなければならない。
     */
    private fun threatPosition(): IntArray {
        val cells = IntArray(size * size)
        cells[at(6, 4)] = Board.BLACK
        cells[at(6, 5)] = Board.BLACK
        cells[at(6, 6)] = Board.BLACK
        for (col in 4 until 8) {
            cells[at(10, col)] = Board.WHITE
        }
        cells[at(10, 3)] = Board.BLACK
        return cells
    }

    @Test
    fun `相手が一手で五連を作れる場合 VCF は必勝を宣言しない`() {
        val cells = threatPosition()
        val searcher = Searcher(size)
        assertEquals(
            "相手が一手で五連を作れる場合、連続した四による攻めは成立しない",
            -1,
            searcher.findVcfWin(cells, Board.BLACK),
        )
    }

    @Test
    fun `上級と最上級も相手の五連成立点を止める`() {
        val cells = threatPosition()
        val engine = AiEngine(size)
        for (difficulty in listOf(Difficulty.NORMAL, Difficulty.HARD, Difficulty.EXPERT)) {
            val move = engine.bestMove(cells.copyOf(), Board.BLACK, difficulty)
            assertEquals(
                "$difficulty は (10,8) に打って白の五連を止めるべき。実際: $move",
                at(10, 8),
                move,
            )
        }
    }

    @Test
    fun `空盤でも合法手を返す`() {
        val cells = IntArray(size * size)
        val searcher = Searcher(size)
        val result = searcher.search(cells, Board.BLACK, maxDepth = 2, timeLimitMs = 3000)
        assertTrue("合法手を返すべき", result.move in cells.indices)
    }

    @Test
    fun `ランダム性のある難易度でも合法手だけを返す`() {
        val cells = IntArray(size * size)
        cells[at(7, 7)] = Board.BLACK
        val searcher = Searcher(size)
        val result = searcher.search(
            cells,
            Board.WHITE,
            maxDepth = 2,
            timeLimitMs = 1000,
            randomness = 0.9,
        )
        assertTrue("合法手を返すべき", result.move in cells.indices)
        assertEquals("既に石がある点には打たない", Board.EMPTY, cells[result.move])
    }

    @Test
    fun `VCF が返す手は実際に必勝の脅威を作る`() {
        // 黒が第 7 行 3～7 列に活三を持ち、四を打てば達四になる
        val cells = IntArray(size * size)
        cells[at(7, 5)] = Board.BLACK
        cells[at(7, 6)] = Board.BLACK
        cells[at(7, 7)] = Board.BLACK
        cells[at(9, 6)] = Board.WHITE

        val searcher = Searcher(size)
        val vcf = searcher.findVcfWin(cells, Board.BLACK)
        assertTrue("黒には四から達四への必勝手順がある。実際: $vcf", vcf >= 0)

        val board = Board(size)
        board.setCells(cells)
        board.play(vcf, Board.BLACK)
        assertTrue(
            "VCF の着手後は「もう 1 枚で五連」の脅威ができていなければならない",
            hasFourThreat(board, Board.BLACK),
        )
    }

    /** 盤上に、その色の「もう 1 枚で五連になる」四が存在するか。 */
    private fun hasFourThreat(board: Board, color: Int): Boolean {
        for (index in board.cells.indices) {
            if (board.cells[index] != Board.EMPTY) continue
            if (board.wouldWinAt(index, color)) return true
        }
        return false
    }

    @Test
    fun `VCF の時間予算が実際に効く`() {
        val cells = IntArray(size * size)
        for (index in cells.indices step 2) {
            cells[index] = Board.BLACK
        }
        val searcher = Searcher(size)
        val started = System.nanoTime()
        val vcf = searcher.findVcfWin(cells, Board.BLACK, timeLimitMs = 1L)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertEquals("制限時間を使い切ったら必勝読みを諦める", -1, vcf)
        assertTrue("1ms 予算で探索を続けてはならない。実際 ${elapsedMs}ms", elapsedMs < 1_000)
    }

    @Test
    fun `極端に短い制限時間でも候補手を回しきらない`() {
        val cells = IntArray(size * size)
        for (col in 3 until 9) {
            cells[at(7, col)] = Board.BLACK
            cells[at(5, col)] = Board.WHITE
        }
        val searcher = Searcher(size)
        val started = System.nanoTime()
        val result = searcher.search(cells, Board.BLACK, maxDepth = 8, timeLimitMs = 1L)
        val elapsedMs = (System.nanoTime() - started) / 1_000_000
        assertTrue("合法手が必要。実際: ${result.move}", result.move in cells.indices)
        assertTrue("1ms 予算では根ノードで早めに切り上げるべき。実際 ${elapsedMs}ms", elapsedMs < 1_000)
    }
}
