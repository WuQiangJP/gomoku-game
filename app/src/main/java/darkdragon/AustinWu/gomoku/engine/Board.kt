package darkdragon.AustinWu.gomoku.engine

/**
 * 五目並べの盤面の基本状態：交点の占有状況と Zobrist ハッシュ。
 *
 * 座標系：index = row * size + col。row / col はいずれも 0 から始まる。
 */
class Board(val size: Int = DEFAULT_SIZE) {

    companion object {
        const val DEFAULT_SIZE = 15

        /** 空点。 */
        const val EMPTY = 0

        /** 黒石。 */
        const val BLACK = 1

        /** 白石。 */
        const val WHITE = 2

        /** 固定の乱数シード。同一の実行内でハッシュ表を再現可能にする。 */
        private const val ZOBRIST_SEED = 0x5EEDC0FFEEL

        /** 五連判定で使い回す 4 方向。ホットパスで配列を繰り返し生成しないため。 */
        internal val DIRECTIONS = arrayOf(
            intArrayOf(1, 0),
            intArrayOf(0, 1),
            intArrayOf(1, 1),
            intArrayOf(1, -1),
        )

        fun createZobristTable(size: Int): LongArray {
            val random = java.util.Random(ZOBRIST_SEED)
            val table = LongArray(size * size * 2)
            for (i in table.indices) {
                table[i] = random.nextLong()
            }
            return table
        }

        fun opponent(color: Int): Int = if (color == BLACK) WHITE else BLACK
    }

    val cells = IntArray(size * size)

    private val zobrist = createZobristTable(size)

    /** 現在局面の Zobrist ハッシュ。 */
    var hash: Long = 0L
        private set

    fun copy(): Board {
        val other = Board(size)
        System.arraycopy(cells, 0, other.cells, 0, cells.size)
        other.hash = hash
        return other
    }

    fun colorAt(index: Int): Int = cells[index]

    fun isEmpty(index: Int): Boolean = cells[index] == EMPTY

    /**
     * 盤面の内容を丸ごと差し替え、Zobrist ハッシュを再計算する。
     * 局面を探索器へ渡して素早く初期化するために使う。
     */
    fun setCells(source: IntArray) {
        require(source.size == cells.size) { "盤のサイズが一致しない" }
        System.arraycopy(source, 0, cells, 0, cells.size)
        hash = 0L
        for (index in cells.indices) {
            val color = cells[index]
            if (color == BLACK || color == WHITE) {
                hash = hash xor zobrist[index * 2 + (color - 1)]
            }
        }
    }

    fun play(index: Int, color: Int) {
        require(cells[index] == EMPTY) { "位置 $index には既に石がある" }
        require(color == BLACK || color == WHITE) { "不正な色 $color" }
        cells[index] = color
        hash = hash xor zobrist[index * 2 + (color - 1)]
    }

    fun unplay(index: Int, color: Int) {
        require(cells[index] == color) { "位置 $index は色 $color ではない" }
        cells[index] = EMPTY
        hash = hash xor zobrist[index * 2 + (color - 1)]
    }

    /**
     * [index] に [color] の石を打ったとみなして五連になるか調べる。
     * 五連（またはそれ以上）を構成する交点列を返し、五連でなければ null を返す。
     */
    fun winningCells(index: Int, color: Int): IntArray? {
        if (cells[index] != color) return null
        val row = index / size
        val col = index % size
        for (direction in DIRECTIONS) {
            val line = ArrayList<Int>(5)
            line.add(index)

            var r = row + direction[0]
            var c = col + direction[1]
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == color) {
                line.add(r * size + c)
                r += direction[0]
                c += direction[1]
            }

            r = row - direction[0]
            c = col - direction[1]
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == color) {
                line.add(0, r * size + c)
                r -= direction[0]
                c -= direction[1]
            }

            if (line.size >= 5) {
                return line.toIntArray()
            }
        }
        return null
    }

    /** [color] の五連が存在するか（着手点は問わない）。 */
    fun hasFive(color: Int): Boolean {
        for (index in cells.indices) {
            if (cells[index] != color) continue
            if (winningCells(index, color) != null) return true
        }
        return false
    }

    fun isFull(): Boolean {
        for (cell in cells) {
            if (cell == EMPTY) return false
        }
        return true
    }

    /**
     * [index] にある [color] の石が五連を構成しているか。
     *
     * [winningCells] と結果は同じだが、リストを生成しない。探索のホットパスから
     * 高頻度で呼ばれるため。
     */
    fun formsFive(index: Int, color: Int): Boolean {
        if (cells[index] != color) return false
        val row = index / size
        val col = index % size
        for (direction in DIRECTIONS) {
            var count = 1
            var r = row + direction[0]
            var c = col + direction[1]
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == color) {
                count++
                if (count >= 5) return true
                r += direction[0]
                c += direction[1]
            }
            r = row - direction[0]
            c = col - direction[1]
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == color) {
                count++
                if (count >= 5) return true
                r -= direction[0]
                c -= direction[1]
            }
        }
        return false
    }

    /** 空点 [index] に [color] の石を 1 枚置いたとき、ただちに五連になるか。 */
    fun wouldWinAt(index: Int, color: Int): Boolean {
        if (cells[index] != EMPTY) return false
        val row = index / size
        val col = index % size
        for (direction in DIRECTIONS) {
            var count = 1
            var r = row + direction[0]
            var c = col + direction[1]
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == color) {
                count++
                r += direction[0]
                c += direction[1]
            }
            r = row - direction[0]
            c = col - direction[1]
            while (r in 0 until size && c in 0 until size && cells[r * size + c] == color) {
                count++
                r -= direction[0]
                c -= direction[1]
            }
            if (count >= 5) return true
        }
        return false
    }
}
