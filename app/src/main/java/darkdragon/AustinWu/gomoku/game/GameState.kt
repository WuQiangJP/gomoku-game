package darkdragon.AustinWu.gomoku.game

import darkdragon.AustinWu.gomoku.engine.Board
import darkdragon.AustinWu.gomoku.engine.Board.Companion.BLACK
import darkdragon.AustinWu.gomoku.engine.Board.Companion.EMPTY
import darkdragon.AustinWu.gomoku.engine.Board.Companion.WHITE
import darkdragon.AustinWu.gomoku.engine.Board.Companion.opponent

/**
 * 対局状態：着手の正当性、勝敗判定、待った、着手履歴を扱う。
 *
 * ルールは自由五目（freestyle）：黒が先手で交互に打ち、五連で勝ち。禁手は設けない。
 */
class GameState(val size: Int = Board.DEFAULT_SIZE) {

    /** 対局のステートマシン。 */
    enum class Status {
        /** 対局中。 */
        PLAYING,

        /** いずれかが勝った。 */
        FINISHED,

        /** 盤が埋まり、勝者がいない。 */
        DRAW,
    }

    private val board = Board(size)

    /** 着手履歴。各手の着手点を記録する。 */
    private val history = ArrayList<Int>()

    /** 次に打つべき側。 */
    var currentPlayer: Int = BLACK
        private set

    /** 対局の状態。 */
    var status: Status = Status.PLAYING
        private set

    /** 勝者（status が FINISHED のとき有効）。 */
    var winner: Int = EMPTY
        private set

    /** 決め手となった五連の交点の添字。 */
    var winningLine: IntArray? = null
        private set

    /** 現在の盤面のスナップショット（複製を返し、外部から内部状態を書き換えられないようにする）。 */
    fun snapshot(): IntArray = board.cells.copyOf()

    /** 指定位置の石の色。 */
    fun colorAt(index: Int): Int = board.colorAt(index)

    /** 着手履歴（読み取り専用ビュー）。 */
    fun moves(): List<Int> = history.toList()

    /** これまでの手数。 */
    fun moveCount(): Int = history.size

    /**
     * 着手を試みる。
     *
     * @return 着手できたか
     */
    fun play(index: Int): Boolean {
        if (status != Status.PLAYING) return false
        if (index !in 0 until board.cells.size) return false
        if (!board.isEmpty(index)) return false

        board.play(index, currentPlayer)
        history.add(index)

        val line = board.winningCells(index, currentPlayer)
        if (line != null) {
            status = Status.FINISHED
            winner = currentPlayer
            winningLine = line
        } else if (board.isFull()) {
            status = Status.DRAW
        } else {
            currentPlayer = opponent(currentPlayer)
        }
        return true
    }

    /** 直前の一手を取り消す（待った用）。 */
    fun undo(): Boolean {
        if (history.isEmpty()) return false
        val index = history.removeAt(history.size - 1)
        val color = board.colorAt(index)
        board.unplay(index, color)
        status = Status.PLAYING
        winner = EMPTY
        winningLine = null
        currentPlayer = color
        return true
    }

    /** 対局をやり直す。 */
    fun reset(firstPlayer: Int = BLACK) {
        board.cells.indices.forEach { board.cells[it] = EMPTY }
        history.clear()
        currentPlayer = firstPlayer
        status = Status.PLAYING
        winner = EMPTY
        winningLine = null
    }

    /** 手順から局面を復元する（セーブデータの読み込み用）。 */
    fun restore(moves: List<Int>, firstPlayer: Int) {
        reset(firstPlayer)
        for (move in moves) {
            if (status != Status.PLAYING) break
            if (!play(move)) break
        }
    }

    companion object {
        const val COLOR_NONE = EMPTY
        const val COLOR_BLACK = BLACK
        const val COLOR_WHITE = WHITE
    }
}
