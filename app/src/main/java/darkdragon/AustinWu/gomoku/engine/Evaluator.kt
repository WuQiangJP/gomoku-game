package darkdragon.AustinWu.gomoku.engine

/**
 * 局面評価：「線分ごとの石の形」で両者に点数を付ける。
 *
 * 石の形の点数（自側から見た値）：
 *  - 五連     1_000_000
 *  - 達四       100_000（両端が空いている四）
 *  - 四          10_000（片側が止まった四）
 *  - 活三         5_000（両端が空いている三）
 *  - 眠三         1_000
 *  - 両端が空いた二連    500
 *  - 片側が止まった二連  100
 *
 * 1 回の着手が影響するのはその交点を含む 4 本の線だけなので、線ごとに差分で点数を
 * 管理する。着手時・取り消し時に 4 本の線だけ再計算し、合計値は O(1) で求まる。
 */
class Evaluator(private val table: LineTable) {

    companion object {
        private const val EMPTY = '.'
        private const val BLACK = 'B'
        private const val WHITE = 'W'

        const val SCORE_FIVE = 1_000_000
        private const val SCORE_OPEN_FOUR = 100_000
        private const val SCORE_FOUR = 10_000
        private const val SCORE_OPEN_THREE = 5_000
        private const val SCORE_BROKEN_THREE = 3_000
        private const val SCORE_SLEEP_THREE = 1_000
        const val SCORE_OPEN_TWO = 500
        private const val SCORE_SLEEP_TWO = 100
        private const val SCORE_BROKEN_FOUR = 8_000
        private const val SCORE_SINGLE = 10
    }

    /** 各線における両色の生文字（'.' 空点、'B' 黒、'W' 白）。 */
    private val buffer = CharArray(table.bufferSize)

    /** 線ごとの黒側の得点。 */
    private val blackLineScore = IntArray(table.lineCount)

    /** 線ごとの白側の得点。 */
    private val whiteLineScore = IntArray(table.lineCount)

    private var blackTotal = 0
    private var whiteTotal = 0

    init {
        java.util.Arrays.fill(buffer, EMPTY)
    }

    /** 盤面全体を評価器に同期する（対局開始時や局面の取り込み時に呼ぶ）。 */
    fun reset(cells: IntArray) {
        java.util.Arrays.fill(buffer, EMPTY)
        for (lineId in 0 until table.lineCount) {
            val line = table.lines[lineId]
            val offset = table.lineOffsets[lineId]
            for (position in line.indices) {
                buffer[offset + position] = charOf(cells[line[position]])
            }
        }
        blackTotal = 0
        whiteTotal = 0
        for (lineId in 0 until table.lineCount) {
            blackLineScore[lineId] = 0
            whiteLineScore[lineId] = 0
        }
        for (lineId in 0 until table.lineCount) {
            recomputeLine(lineId)
        }
    }

    private fun charOf(cell: Int): Char = when (cell) {
        Board.BLACK -> BLACK
        Board.WHITE -> WHITE
        else -> EMPTY
    }

    /** 着手後に影響を受ける 4 本の線を更新する。 */
    fun onPlay(index: Int, color: Int) {
        val ch = if (color == Board.BLACK) BLACK else WHITE
        for (slot in 0 until 4) {
            val lineId = table.cellLines[index][slot]
            val position = table.cellPositions[index][slot]
            buffer[table.lineOffsets[lineId] + position] = ch
            recomputeLine(lineId)
        }
    }

    /** 着手を取り消した後に影響を受ける 4 本の線を更新する。 */
    fun onUnplay(index: Int) {
        for (slot in 0 until 4) {
            val lineId = table.cellLines[index][slot]
            val position = table.cellPositions[index][slot]
            buffer[table.lineOffsets[lineId] + position] = EMPTY
            recomputeLine(lineId)
        }
    }

    private fun recomputeLine(lineId: Int) {
        val offset = table.lineOffsets[lineId]
        val length = table.lines[lineId].size
        val black = scoreLine(offset, length, BLACK)
        val white = scoreLine(offset, length, WHITE)
        blackTotal += black - blackLineScore[lineId]
        whiteTotal += white - whiteLineScore[lineId]
        blackLineScore[lineId] = black
        whiteLineScore[lineId] = white
    }

    /**
     * 1 本の線を [own] 側から見た得点を計算する。
     *
     * 「連続した石のまとまり＋両端の空き」で数える：
     *  - 連続した自石が 5 個以上なら五連。
     *  - 連続 4 個で両端が空いていれば達四、そうでなければ四。
     *  - 連続 3 個で両端が空いていれば活三、そうでなければ眠三。
     *  - さらに、飛び四（X_XX）や飛び三（XX_X）のような連続しない形を別途数える。
     */
    private fun scoreLine(offset: Int, length: Int, own: Char): Int {
        var score = 0
        var i = 0
        while (i < length) {
            if (buffer[offset + i] != own) {
                i++
                continue
            }
            var end = i
            while (end < length && buffer[offset + end] == own) end++
            val runLength = end - i
            val leftOpen = i > 0 && buffer[offset + i - 1] == EMPTY
            val rightOpen = end < length && buffer[offset + end] == EMPTY

            score += when {
                runLength >= 5 -> SCORE_FIVE
                runLength == 4 -> if (leftOpen && rightOpen) SCORE_OPEN_FOUR else SCORE_FOUR
                runLength == 3 -> if (leftOpen && rightOpen) SCORE_OPEN_THREE else SCORE_SLEEP_THREE
                runLength == 2 -> if (leftOpen && rightOpen) SCORE_OPEN_TWO else SCORE_SLEEP_TWO
                else -> SCORE_SINGLE
            }
            i = end
        }

        // 飛び四：X_XX（1 目空き、5 目の窓に収まる形）
        for (start in 0 until length - 4) {
            if (buffer[offset + start] == own &&
                buffer[offset + start + 1] == EMPTY &&
                buffer[offset + start + 2] == own &&
                buffer[offset + start + 3] == own &&
                buffer[offset + start + 4] == own
            ) {
                score += SCORE_BROKEN_FOUR
            }
        }

        // 飛び三：XX_X と X_XX
        for (start in 0 until length - 3) {
            if (buffer[offset + start] == own &&
                buffer[offset + start + 1] == own &&
                buffer[offset + start + 2] == EMPTY &&
                buffer[offset + start + 3] == own
            ) {
                score += SCORE_BROKEN_THREE
            }
            if (buffer[offset + start] == own &&
                buffer[offset + start + 1] == EMPTY &&
                buffer[offset + start + 2] == own &&
                buffer[offset + start + 3] == own
            ) {
                score += SCORE_BROKEN_THREE
            }
        }

        return score
    }

    /** [side] 側から見た局面点：自側の形の点数から相手側の点数を引いた値。 */
    fun evaluate(side: Int): Int {
        val own = if (side == Board.BLACK) blackTotal else whiteTotal
        val other = if (side == Board.BLACK) whiteTotal else blackTotal
        return own - other
    }
}
