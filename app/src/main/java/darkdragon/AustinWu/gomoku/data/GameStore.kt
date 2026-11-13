package darkdragon.AustinWu.gomoku.data

import android.content.Context
import darkdragon.AustinWu.gomoku.game.Difficulty
import darkdragon.AustinWu.gomoku.game.GameState

/**
 * 対局データの保存：アプリ専用ディレクトリの SharedPreferences だけを使う。
 *
 * アプリ専用データは /data/data/<package>/ に置かれ、Android はアプリの
 * アンインストール時にこのディレクトリごと削除する。したがってアンインストール後に
 * 端末にファイルが残ることはない。アプリはストレージ権限も一切要求していない。
 */
class GameStore(context: Context) {

    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** 難易度を読み込む。 */
    fun loadDifficulty(): Difficulty = Difficulty.fromName(preferences.getString(KEY_DIFFICULTY, null))

    /** 難易度を保存する。 */
    fun saveDifficulty(difficulty: Difficulty) {
        preferences.edit().putString(KEY_DIFFICULTY, difficulty.name).apply()
    }

    /** プレイヤーの持ち色を読み込む。 */
    fun loadHumanColor(): Int = preferences.getInt(KEY_HUMAN_COLOR, GameState.COLOR_BLACK)

    /** プレイヤーの持ち色を保存する。 */
    fun saveHumanColor(color: Int) {
        preferences.edit().putInt(KEY_HUMAN_COLOR, color).apply()
    }

    /** 前回の対局の手順を読み込む。 */
    fun loadMoves(): List<Int> {
        val raw = preferences.getString(KEY_MOVES, null) ?: return emptyList()
        if (raw.isEmpty()) return emptyList()
        return raw.split(",").mapNotNull { it.trim().toIntOrNull() }
    }

    /** 手順を保存する。 */
    fun saveMoves(moves: List<Int>) {
        preferences.edit().putString(KEY_MOVES, moves.joinToString(",")).apply()
    }

    /** セーブデータを消去する。 */
    fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "gogame_state"
        const val KEY_DIFFICULTY = "difficulty"
        const val KEY_HUMAN_COLOR = "human_color"
        const val KEY_MOVES = "moves"
    }
}
