package darkdragon.AustinWu.gomoku.game

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import darkdragon.AustinWu.gomoku.data.GameStore
import darkdragon.AustinWu.gomoku.engine.Board
import darkdragon.AustinWu.gomoku.engine.Board.Companion.BLACK
import darkdragon.AustinWu.gomoku.engine.Board.Companion.WHITE
import darkdragon.AustinWu.gomoku.game.GameState.Status
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 画面状態のスナップショット。
 */
data class GameUiState(
    val board: IntArray = IntArray(Board.DEFAULT_SIZE * Board.DEFAULT_SIZE),
    val status: Status = Status.PLAYING,
    val currentPlayer: Int = BLACK,
    val winner: Int = 0,
    val winningLine: IntArray? = null,
    val lastMove: Int = -1,
    val moveHistory: List<Int> = emptyList(),
    val difficulty: Difficulty = Difficulty.NORMAL,
    val humanColor: Int = BLACK,
    val aiThinking: Boolean = false,
    val message: String = "石を打ってください",
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is GameUiState) return false
        return status == other.status &&
            currentPlayer == other.currentPlayer &&
            winner == other.winner &&
            board.contentEquals(other.board) &&
            sameArray(winningLine, other.winningLine) &&
            lastMove == other.lastMove &&
            moveHistory == other.moveHistory &&
            difficulty == other.difficulty &&
            humanColor == other.humanColor &&
            aiThinking == other.aiThinking &&
            message == other.message
    }

    /** 配列の内容比較。null 同士は等しく、null と非 null は等しくない。 */
    private fun sameArray(a: IntArray?, b: IntArray?): Boolean {
        if (a == null || b == null) return a == null && b == null
        return a.contentEquals(b)
    }

    override fun hashCode(): Int {
        var result = board.contentHashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + currentPlayer
        result = 31 * result + winner
        result = 31 * result + (winningLine?.contentHashCode() ?: 0)
        result = 31 * result + lastMove
        result = 31 * result + moveHistory.hashCode()
        result = 31 * result + difficulty.hashCode()
        result = 31 * result + humanColor
        result = 31 * result + aiThinking.hashCode()
        result = 31 * result + message.hashCode()
        return result
    }
}

/**
 * 対局用 ViewModel：画面状態、AI の思考、セーブデータを管理する。
 */
open class GameViewModel(application: Application) : AndroidViewModel(application) {

    private val store = GameStore(application)
    private val state = GameState()
    private val aiEngine = AiEngine(Board.DEFAULT_SIZE)

    private val _uiState = MutableStateFlow(GameUiState())
    val uiState: StateFlow<GameUiState> = _uiState.asStateFlow()

    private var aiJob: Job? = null

    init {
        val difficulty = store.loadDifficulty()
        val humanColor = store.loadHumanColor()
        // 五目並べは黒が先手と決まっている。「先手の色」と「プレイヤーがどちらを持つか」は
        // 別の事柄であり、後者は操作する側を決めるだけである
        state.restore(store.loadMoves(), firstPlayer = BLACK)
        _uiState.value = _uiState.value.copy(difficulty = difficulty, humanColor = humanColor)
        publish(
            when (state.status) {
                Status.PLAYING ->
                    if (state.currentPlayer == humanColor) "石を打ってください" else "AI が思考中…"
                Status.FINISHED -> "%s の勝ち！".format(colorName(state.winner))
                Status.DRAW -> "盤が埋まり、引き分けです"
            },
        )
        // セーブデータの復元後に「AI の手番」だった場合は、ここで必ず探索を再開する。
        // そうしないと、画面はプレイヤーを待ち続けて止まる
        if (state.status == Status.PLAYING && state.currentPlayer != humanColor) {
            scheduleAiMove()
        }
    }

    /** プレイヤーの着手。 */
    fun onCellClicked(index: Int) {
        val snapshot = _uiState.value
        if (snapshot.aiThinking) return
        if (snapshot.status != Status.PLAYING) return
        if (snapshot.currentPlayer != snapshot.humanColor) return

        if (!state.play(index)) return
        store.saveMoves(state.moves())
        publish(messageAfterMove())

        if (state.status == Status.PLAYING) {
            scheduleAiMove()
        }
    }

    /** 難易度を切り替える。 */
    fun setDifficulty(difficulty: Difficulty) {
        store.saveDifficulty(difficulty)
        _uiState.value = _uiState.value.copy(difficulty = difficulty)
    }

    /** 持ち色を入れ替えて対局をやり直す（先手は常に黒）。 */
    fun swapSides() {
        val newHuman = if (_uiState.value.humanColor == BLACK) WHITE else BLACK
        store.saveHumanColor(newHuman)
        aiJob?.cancel()
        state.reset(firstPlayer = BLACK)
        store.saveMoves(emptyList())
        _uiState.value = _uiState.value.copy(humanColor = newHuman)
        publish("色を入れ替えました。黒が先手です")
        if (state.currentPlayer != newHuman) {
            scheduleAiMove()
        }
    }

    /** 待った：プレイヤーが打つ前の状態まで戻す（プレイヤーの一手と AI の受けを取り消す）。 */
    fun undo() {
        aiJob?.cancel()
        if (!state.undo()) return

        // 「プレイヤーの手番になるまで」取り消し続ける。黒を持つ場合は自分の一手と AI の受け、
        // 白を持つ場合も同様。AI がまだ思考中（未着手）ならプレイヤーの一手だけを戻す。
        while (state.currentPlayer != _uiState.value.humanColor && state.moveCount() > 0) {
            if (!state.undo()) break
        }

        store.saveMoves(state.moves())
        publish("待ったで戻しました")
        if (state.status == Status.PLAYING && state.currentPlayer != _uiState.value.humanColor) {
            scheduleAiMove()
        }
    }

    /** 対局をやり直す（先手は常に黒）。 */
    fun restart() {
        aiJob?.cancel()
        val human = _uiState.value.humanColor
        state.reset(firstPlayer = BLACK)
        store.saveMoves(emptyList())
        publish("新しい対局を始めます。黒が先手です")
        if (state.currentPlayer != human) {
            scheduleAiMove()
        }
    }

    /** AI に一手打たせる。 */
    private fun scheduleAiMove() {
        aiJob?.cancel()
        val side = state.currentPlayer
        val cells = state.snapshot()
        val difficulty = _uiState.value.difficulty
        _uiState.value = _uiState.value.copy(aiThinking = true, message = "AI が思考中…")
        aiJob = viewModelScope.launch {
            // 「思考中」の状態を画面に先に反映させる
            delay(16)
            val job = coroutineContext[Job]
            val move = withContext(aiDispatcher) {
                // キャンセル後は探索が直近のチェックポイントで抜け、前の対局の探索が
                // CPU を占有し続けるのを防ぐ
                computeAiMove(cells, side, difficulty) { job?.isActive == false }
            }
            if (move >= 0 && state.play(move)) {
                store.saveMoves(state.moves())
                publish(messageAfterMove())
            } else {
                _uiState.value = _uiState.value.copy(aiThinking = false, message = "AI が着手できません")
            }
        }
    }

    /**
     * AI の着手点を計算する。テストはこのメソッドを決定的な実装で上書きでき、
     * 本番では [AiEngine] を使う。
     */
    protected open fun computeAiMove(
        cells: IntArray,
        side: Int,
        difficulty: Difficulty,
        isCancelled: () -> Boolean,
    ): Int = aiEngine.bestMove(cells, side, difficulty, isCancelled)

    /** AI 計算に使うディスパッチャ。テストではテスト用ディスパッチャに差し替えて決定的にする。 */
    protected open val aiDispatcher: CoroutineDispatcher
        get() = Dispatchers.Default

    private fun messageAfterMove(): String {
        return when (state.status) {
            Status.PLAYING -> if (state.currentPlayer == _uiState.value.humanColor) "石を打ってください" else "AI が思考中…"
            Status.FINISHED -> "%s の勝ち！".format(colorName(state.winner))
            Status.DRAW -> "盤が埋まり、引き分けです"
        }
    }

    private fun publish(message: String) {
        val history = state.moves()
        _uiState.value = _uiState.value.copy(
            board = state.snapshot(),
            status = state.status,
            currentPlayer = state.currentPlayer,
            winner = state.winner,
            winningLine = state.winningLine,
            lastMove = history.lastOrNull() ?: -1,
            moveHistory = history,
            aiThinking = false,
            message = message,
        )
    }

    private fun colorName(color: Int): String = if (color == BLACK) "黒番" else "白番"
}
