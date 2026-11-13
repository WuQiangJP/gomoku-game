package darkdragon.AustinWu.gomoku.game

import darkdragon.AustinWu.gomoku.engine.LineTable
import darkdragon.AustinWu.gomoku.engine.Searcher

/**
 * AI の難易度。
 *
 * - [maxDepth]：反復深化における探索の最大の深さ。
 * - [timeLimitMs]：1 手あたりの思考時間の上限。
 * - [randomness]：初級において、許容できる候補手からランダムに選ぶ度合い（0 なら常に最善手）。
 */
enum class Difficulty(
    val displayName: String,
    val maxDepth: Int,
    val timeLimitMs: Long,
    val randomness: Double,
    val useVcf: Boolean,
) {
    EASY("初級", 2, 300L, 0.45, false),
    NORMAL("中級", 4, 800L, 0.0, false),
    HARD("上級", 6, 2000L, 0.0, true),
    EXPERT("最上級", 8, 4000L, 0.0, true);

    companion object {
        fun fromName(name: String?): Difficulty {
            return entries.firstOrNull { it.name == name } ?: NORMAL
        }
    }
}

/**
 * AI エンジンのファサード：探索器と難易度パラメータをまとめ、画面層から呼びやすくする。
 *
 * 線分表は読み取り専用なので呼び出しをまたいで使い回せる。一方 [Searcher] は内部に
 * 可変な盤面・評価器・置換表を抱えるため、計算のたびに新しいインスタンスを生成する。
 * 画面側は AI の思考中にただちに対局をやり直したり待ったをしたりでき、インスタンスを
 * 共有すると 2 つの探索が同じ局面を同時に書き換えて例外を投げてしまう。
 */
class AiEngine(private val size: Int) {

    private val lineTable = LineTable(size)

    /**
     * 最善手を計算する。
     *
     * @param cells 現在の盤面
     * @param side 手番側の色
     * @param difficulty 難易度
     * @param isCancelled 画面側のキャンセルコールバック。true を返すと探索は早期に切り上げる
     * @return 着手点の添字。合法手がなければ -1
     */
    fun bestMove(
        cells: IntArray,
        side: Int,
        difficulty: Difficulty,
        isCancelled: () -> Boolean = { false },
    ): Int {
        val searcher = Searcher(size, lineTable)
        if (difficulty.useVcf) {
            val vcf = searcher.findVcfWin(cells, side, isCancelled = isCancelled)
            if (vcf >= 0) return vcf
        }
        val result = searcher.search(
            cells = cells,
            side = side,
            maxDepth = difficulty.maxDepth,
            timeLimitMs = difficulty.timeLimitMs,
            randomness = difficulty.randomness,
            isCancelled = isCancelled,
        )
        return result.move
    }
}
