package darkdragon.AustinWu.gomoku.engine

/**
 * 盤上の線分を事前計算した表：盤を 4 方向の全線分に分解する。
 *
 * 評価関数は探索の各ノードで石の形を素早く数える必要があり、線分を先に用意しておけば
 * 座標の導出を繰り返さずに済む。各交点はちょうど 4 本の線（横、縦、2 種の斜め）に
 * 属するため、[cellLines] / [cellPositions] はいずれも交点ごとに 4 要素の固定長配列になる。
 */
class LineTable(val size: Int) {

    /** 各線分に含まれる盤の添字。並び順はその方向に沿った順。 */
    val lines: List<IntArray>

    /** 線分の総数。15x15 の盤では 15 + 15 + 29 + 29 = 88。 */
    val lineCount: Int

    /** コンパクトなバッファ内での各線分の開始オフセット。 */
    val lineOffsets: IntArray

    /** コンパクトなバッファの全長。各交点が 4 本の線に 1 回ずつ現れる。 */
    val bufferSize: Int

    /** 各交点が属する 4 本の線の番号。 */
    val cellLines: Array<IntArray>

    /** 各交点の、属する 4 本の線それぞれにおける位置。 */
    val cellPositions: Array<IntArray>

    init {
        val builder = ArrayList<IntArray>()
        val directions = arrayOf(
            intArrayOf(0, 1),
            intArrayOf(1, 0),
            intArrayOf(1, 1),
            intArrayOf(1, -1),
        )
        for (direction in directions) {
            for (row in 0 until size) {
                for (col in 0 until size) {
                    val previousRow = row - direction[0]
                    val previousCol = col - direction[1]
                    if (previousRow in 0 until size && previousCol in 0 until size) continue
                    val cells = ArrayList<Int>(size)
                    var r = row
                    var c = col
                    while (r in 0 until size && c in 0 until size) {
                        cells.add(r * size + c)
                        r += direction[0]
                        c += direction[1]
                    }
                    builder.add(cells.toIntArray())
                }
            }
        }
        lines = builder
        lineCount = builder.size

        val offsets = IntArray(lineCount)
        var base = 0
        for (i in 0 until lineCount) {
            offsets[i] = base
            base += builder[i].size
        }
        lineOffsets = offsets
        bufferSize = base

        val linesPerCell = Array(size * size) { IntArray(4) { -1 } }
        val positionsPerCell = Array(size * size) { IntArray(4) { -1 } }
        for (lineId in 0 until lineCount) {
            val line = builder[lineId]
            for (position in line.indices) {
                val cell = line[position]
                var slot = 0
                while (slot < 4 && linesPerCell[cell][slot] >= 0) slot++
                check(slot < 4) { "交点 $cell が属する線分が 4 本を超えた" }
                linesPerCell[cell][slot] = lineId
                positionsPerCell[cell][slot] = position
            }
        }
        cellLines = linesPerCell
        cellPositions = positionsPerCell
    }
}
