package darkdragon.AustinWu.gomoku.engine

import darkdragon.AustinWu.gomoku.engine.Board.Companion.BLACK
import darkdragon.AustinWu.gomoku.engine.Board.Companion.EMPTY
import darkdragon.AustinWu.gomoku.engine.Board.Companion.WHITE
import darkdragon.AustinWu.gomoku.engine.Board.Companion.opponent

/**
 * 探索器：negamax + alpha-beta + 置換表 + 反復深化。さらに四追い（VCF）による
 * 必勝手順の読みを実装する。
 *
 * 必勝読みの流れ：
 *  1. 現在の側が「達四」（両端が空いた四）を作れれば、相手は同時に両端を止められず、
 *     必勝読み成立と判定する。
 *  2. 「四」（五連になる点が 1 つだけの形）を打った場合、相手はその 1 点を受けざるを得ず、
 *     次の手を再帰的に調べる。
 *  3. ただちに五連を作れるなら、その時点で勝ち。
 */
class Searcher(
    private val size: Int = Board.DEFAULT_SIZE,
    private val table: LineTable = LineTable(size),
) {

    companion object {
        /** 探索点数の上限。勝敗と通常の局面点を区別するために使う。 */
        const val MATE = 5_000_000

        private const val WIN_SCORE = 10_000_000
        private const val MAX_VCF_DEPTH = 4
    }

    private class TTEntry(val depth: Int, val score: Int, val flag: Int, val move: Int)

    private val board = Board(size)
    private val evaluator = Evaluator(table)
    private val transpositionTable = HashMap<Long, TTEntry>()
    private val history = IntArray(size * size)

    /** 候補手マーク用の配列：neighborStamp[i] == stamp なら、その点は既存の石から 2 目以内。 */
    private val neighborStamp = IntArray(size * size)
    private var stamp = 0

    private var deadline = 0L
    private var aborted = false
    private var nodes = 0L

    /** 外部からのキャンセルコールバック（画面が局面を切り替えた際に探索へ早期終了を伝える）。 */
    private var cancelled: () -> Boolean = { false }

    /** 探索をただちに止めるべきか：キャンセル済み、中止済み、または制限時間超過。 */
    private fun shouldStop(): Boolean {
        return aborted || cancelled() || System.nanoTime() > deadline
    }

    /** 今回の探索で使う局面（ゲームの局面とは分離し、探索終了後に捨てる）。 */
    fun search(
        cells: IntArray,
        side: Int,
        maxDepth: Int,
        timeLimitMs: Long,
        randomness: Double = 0.0,
        isCancelled: () -> Boolean = { false },
    ): SearchResult {
        board.setCells(cells)
        evaluator.reset(board.cells)
        transpositionTable.clear()
        history.fill(0)
        aborted = false
        cancelled = isCancelled
        nodes = 0
        deadline = System.nanoTime() + timeLimitMs * 1_000_000L

        var bestMove = -1
        var bestScore = 0
        var completedDepth = 0

        for (depth in 1..maxDepth) {
            val result = rootSearch(depth, side)
            if (aborted) break
            bestMove = result.move
            bestScore = result.score
            completedDepth = depth
            // 必勝の決め手が見つかったので、これ以上深くする必要はない
            if (bestScore >= MATE) break
        }

        if (bestMove < 0) {
            val fallback = candidateMoves().firstOrNull() ?: -1
            return SearchResult(fallback, 0, 0, nodes)
        }

        if (randomness > 0.0 && completedDepth <= 2) {
            val alternatives = collectAlternatives(bestMove, side, randomness)
            if (alternatives.isNotEmpty()) {
                return SearchResult(alternatives.random(), bestScore, completedDepth, nodes)
            }
        }

        return SearchResult(bestMove, bestScore, completedDepth, nodes)
    }

    private class RootResult(val move: Int, val score: Int)

    private fun rootSearch(depth: Int, side: Int): RootResult {
        var alpha = -WIN_SCORE
        val beta = WIN_SCORE
        var bestMove = -1
        var bestScore = -WIN_SCORE

        for (move in orderedMoves(side)) {
            // 根ノードでもタイムアウトとキャンセルを検査する。短い時間予算で候補手を
            // 最後まで回しきらないため
            if (shouldStop()) {
                aborted = true
                break
            }
            board.play(move, side)
            evaluator.onPlay(move, side)
            val score = if (board.formsFive(move, side)) {
                MATE
            } else {
                -negamax(depth - 1, -beta, -alpha, opponent(side), 1)
            }
            evaluator.onUnplay(move)
            board.unplay(move, side)
            if (aborted) break

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > alpha) alpha = score
        }
        return RootResult(bestMove, bestScore)
    }

    private fun negamax(depth: Int, alphaIn: Int, betaIn: Int, side: Int, ply: Int): Int {
        var alpha = alphaIn
        var beta = betaIn
        nodes++
        if ((nodes and 1023L) == 0L && shouldStop()) {
            aborted = true
            return 0
        }
        if (aborted) return 0

        val alphaOrig = alpha
        val hash = board.hash
        val entry = transpositionTable[hash]
        if (entry != null && entry.depth >= depth) {
            when (entry.flag) {
                0 -> return entry.score
                1 -> if (entry.score > alpha) alpha = entry.score
                2 -> if (entry.score < beta) beta = entry.score
            }
            if (alpha >= beta) return entry.score
        }

        if (depth <= 0) return evaluator.evaluate(side)

        val moves = orderedMoves(side)
        if (moves.isEmpty()) return 0

        var bestMove = -1
        var bestScore = -WIN_SCORE
        for (move in moves) {
            board.play(move, side)
            evaluator.onPlay(move, side)
            val score = if (board.formsFive(move, side)) {
                MATE - ply
            } else {
                -negamax(depth - 1, -beta, -alpha, opponent(side), ply + 1)
            }
            evaluator.onUnplay(move)
            board.unplay(move, side)
            if (aborted) return 0

            if (score > bestScore) {
                bestScore = score
                bestMove = move
            }
            if (score > alpha) alpha = score
            if (alpha >= beta) break
        }

        history[bestMove] += depth * depth
        val flag = when {
            bestScore <= alphaOrig -> 2
            bestScore >= beta -> 1
            else -> 0
        }
        transpositionTable[hash] = TTEntry(depth, bestScore, flag, bestMove)
        return bestScore
    }

    /** 候補手：既に石がある点の周囲の空点。空盤なら中央付近を返す。 */
    private fun candidateMoves(): IntArray {
        var stones = 0
        for (cell in board.cells) {
            if (cell != EMPTY) stones++
        }
        if (stones == 0) {
            val center = size / 2
            return intArrayOf(center * size + center)
        }

        // 増加するスタンプ番号で「既存の石から 2 目以内」の空点をマークし、
        // 交点ごとに近傍を走査する重複を避ける
        stamp++
        if (stamp == Int.MAX_VALUE) {
            java.util.Arrays.fill(neighborStamp, 0)
            stamp = 1
        }
        for (index in board.cells.indices) {
            if (board.cells[index] == EMPTY) continue
            val row = index / size
            val col = index % size
            for (dr in -2..2) {
                val r = row + dr
                if (r !in 0 until size) continue
                for (dc in -2..2) {
                    val c = col + dc
                    if (c !in 0 until size) continue
                    neighborStamp[r * size + c] = stamp
                }
            }
        }

        val result = ArrayList<Int>(stones * 6)
        for (index in board.cells.indices) {
            if (board.cells[index] == EMPTY && neighborStamp[index] == stamp) {
                result.add(index)
            }
        }
        if (result.isEmpty()) {
            for (index in board.cells.indices) {
                if (board.cells[index] == EMPTY) {
                    result.add(index)
                }
            }
        }
        return result.toIntArray()
    }

    /** 着手の並べ替え：相手の次の五連成立点を止める手を最優先し、次に局所的な脅威点で並べる。 */
    private fun orderedMoves(side: Int): IntArray {
        val candidates = candidateMoves()
        if (candidates.size <= 1) return candidates

        val opp = opponent(side)
        val mustBlock = opponentWinningPoints(opp, candidates)
        val scored = candidates.map { index ->
            val blockingBonus = if (mustBlock.contains(index)) 1_000_000 else 0
            blockingBonus + moveHeuristic(index, side)
        }
        val order = scored.indices.sortedByDescending { scored[it] + history[candidates[it]] }
        return IntArray(order.size) { candidates[order[it]] }
    }

    /** 相手が次の一手で五連を作れる位置の集合。 */
    private fun opponentWinningPoints(opp: Int, candidates: IntArray): Set<Int> {
        var points: HashSet<Int>? = null
        for (index in candidates) {
            if (board.wouldWinAt(index, opp)) {
                if (points == null) points = HashSet<Int>(8)
                points.add(index)
            }
        }
        return points ?: emptySet()
    }

    /** 局所ヒューリスティック：その点の周囲にある自側・相手側の連なりの数を数える。 */
    private fun moveHeuristic(index: Int, side: Int): Int {
        val row = index / size
        val col = index % size
        val opp = opponent(side)
        var score = 0
        for (direction in Board.DIRECTIONS) {
            var own = 0
            var enemy = 0
            var r = row + direction[0]
            var c = col + direction[1]
            while (r in 0 until size && c in 0 until size && board.cells[r * size + c] == side) {
                own++
                r += direction[0]
                c += direction[1]
            }
            r = row - direction[0]
            c = col - direction[1]
            while (r in 0 until size && c in 0 until size && board.cells[r * size + c] == side) {
                own++
                r -= direction[0]
                c -= direction[1]
            }
            r = row + direction[0]
            c = col + direction[1]
            while (r in 0 until size && c in 0 until size && board.cells[r * size + c] == opp) {
                enemy++
                r += direction[0]
                c += direction[1]
            }
            r = row - direction[0]
            c = col - direction[1]
            while (r in 0 until size && c in 0 until size && board.cells[r * size + c] == opp) {
                enemy++
                r -= direction[0]
                c -= direction[1]
            }
            score += own * own * 12 + enemy * enemy * 8
        }
        return score
    }

    /** 初級：最善手の近くから、許容できる手をランダムに選ぶ。 */
    private fun collectAlternatives(bestMove: Int, side: Int, randomness: Double): List<Int> {
        val result = ArrayList<Int>()
        val best = moveHeuristic(bestMove, side)
        val threshold = best * (1.0 - randomness) - 40
        for (move in candidateMoves()) {
            if (move == bestMove) continue
            if (moveHeuristic(move, side) >= threshold) result.add(move)
        }
        return result
    }

    /**
     * 四追い（VCF）による必勝手順の検出：成立した場合はその初手を、不成立なら -1 を返す。
     *
     * この判定が成立するのは「相手に 1 手で五連を作る脅威がない」場合だけである。
     * 連続した四で攻めるには相手が毎手受けざるを得ないことが前提であり、相手に別の
     * 五連成立点がある時点で自側の四は強制力を失う。その場合は主探索の受けに委ねる。
     */
    fun findVcfWin(
        cells: IntArray,
        side: Int,
        timeLimitMs: Long = 2_000L,
        isCancelled: () -> Boolean = { false },
    ): Int {
        board.setCells(cells)
        evaluator.reset(board.cells)
        transpositionTable.clear()
        history.fill(0)
        aborted = false
        cancelled = isCancelled
        nodes = 0
        deadline = System.nanoTime() + timeLimitMs * 1_000_000L
        return vcfSearch(side, MAX_VCF_DEPTH)
    }

    private fun vcfSearch(side: Int, depth: Int): Int {
        if (shouldStop()) {
            aborted = true
            return -1
        }
        if (depth <= 0) return -1

        val opp = opponent(side)
        val candidates = candidateMoves()
        // 相手が現時点で 1 手五連を作れる点。自ら即座に五連を作れる場合を除き、
        // 四による攻めは成立しない
        val oppThreats = opponentWinningPoints(opp, candidates)

        for (move in candidates) {
            if (board.cells[move] != EMPTY) continue
            board.play(move, side)
            evaluator.onPlay(move, side)

            if (board.formsFive(move, side)) {
                evaluator.onUnplay(move)
                board.unplay(move, side)
                return move
            }

            // 着手後も相手に別の五連成立点が残る（この手で脅威を消しきれなかった）場合は
            // 四による攻めは無効
            if (oppThreats.isNotEmpty() && !(oppThreats.size == 1 && oppThreats.contains(move))) {
                evaluator.onUnplay(move)
                board.unplay(move, side)
                continue
            }

            val points = fourCompletionPoints(move, side)
            if (points.size >= 2) {
                // 達四：相手は 2 つの五連成立点を同時に止められない
                evaluator.onUnplay(move)
                board.unplay(move, side)
                return move
            } else if (points.size == 1) {
                // 四：相手は唯一の五連成立点を受けざるを得ない
                val block = points.first()
                board.play(block, opp)
                evaluator.onPlay(block, opp)
                val result = vcfSearch(side, depth - 1)
                evaluator.onUnplay(block)
                board.unplay(block, opp)
                if (result >= 0) {
                    evaluator.onUnplay(move)
                    board.unplay(move, side)
                    return move
                }
            }

            evaluator.onUnplay(move)
            board.unplay(move, side)
            if (aborted) return -1
        }
        return -1
    }

    /**
     * [index] に [side] の石があるとして、その方向で五連を完成させられる補完点を求める。
     * 空集合なら四ではない。2 点なら達四、1 点なら四を表す。
     */
    private fun fourCompletionPoints(index: Int, side: Int): Set<Int> {
        val row = index / size
        val col = index % size
        val points = HashSet<Int>()
        for (direction in Board.DIRECTIONS) {
            var count = 1
            val lineStart = ArrayList<Int>()
            lineStart.add(index)

            var r = row + direction[0]
            var c = col + direction[1]
            while (r in 0 until size && c in 0 until size && board.cells[r * size + c] == side) {
                count++
                lineStart.add(r * size + c)
                r += direction[0]
                c += direction[1]
            }
            r = row - direction[0]
            c = col - direction[1]
            while (r in 0 until size && c in 0 until size && board.cells[r * size + c] == side) {
                count++
                lineStart.add(0, r * size + c)
                r -= direction[0]
                c -= direction[1]
            }
            if (count != 4) continue

            // 両端が空いていれば、そこに打つことで五連になる
            val head = lineStart.first()
            val tail = lineStart.last()
            val headRow = head / size
            val headCol = head % size
            val tailRow = tail / size
            val tailCol = tail % size
            val headPrevRow = headRow - direction[0]
            val headPrevCol = headCol - direction[1]
            val tailNextRow = tailRow + direction[0]
            val tailNextCol = tailCol + direction[1]
            if (headPrevRow in 0 until size && headPrevCol in 0 until size &&
                board.cells[headPrevRow * size + headPrevCol] == EMPTY
            ) {
                points.add(headPrevRow * size + headPrevCol)
            }
            if (tailNextRow in 0 until size && tailNextCol in 0 until size &&
                board.cells[tailNextRow * size + tailNextCol] == EMPTY
            ) {
                points.add(tailNextRow * size + tailNextCol)
            }
        }
        return points
    }

    /** 探索結果。 */
    data class SearchResult(
        val move: Int,
        val score: Int,
        val depth: Int,
        val nodes: Long,
    )
}
