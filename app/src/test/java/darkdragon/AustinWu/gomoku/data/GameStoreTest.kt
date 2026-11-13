package darkdragon.AustinWu.gomoku.data

import android.content.Context
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import darkdragon.AustinWu.gomoku.game.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * セーブデータ読み書きのテスト：アプリ専用 SharedPreferences にだけ保存され、
 * そのまま取り出せることを確認する。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GameStoreTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun freshStore(): GameStore = GameStore(context).also { it.clear() }

    @Test
    fun `難易度と持ち色は往復して読み書きできる`() {
        freshStore()
        assertEquals(Difficulty.NORMAL, GameStore(context).loadDifficulty())

        GameStore(context).saveDifficulty(Difficulty.EXPERT)
        GameStore(context).saveHumanColor(2)

        assertEquals(Difficulty.EXPERT, GameStore(context).loadDifficulty())
        assertEquals(2, GameStore(context).loadHumanColor())
    }

    @Test
    fun `手順は往復して読み書きでき消去もできる`() {
        val store = freshStore()
        assertTrue("初期状態は空であるべき", store.loadMoves().isEmpty())

        store.saveMoves(listOf(112, 96, 94))
        assertEquals(listOf(112, 96, 94), GameStore(context).loadMoves())

        store.clear()
        assertTrue("消去後に着手が残ってはならない", GameStore(context).loadMoves().isEmpty())
        assertEquals(Difficulty.NORMAL, GameStore(context).loadDifficulty())
    }

    @Test
    fun `セーブデータはアプリ専用ディレクトリにのみ書き込まれる`() {
        val store = freshStore()
        store.saveMoves(listOf(0, 1, 2))

        val privateFile = context.getSharedPreferences("gogame_state", Context.MODE_PRIVATE)
        assertEquals(
            "手順は専用 SharedPreferences に保存されるべき",
            "0,1,2",
            privateFile.getString("moves", null),
        )
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.filterNot { it.endsWith("DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION") }
            .orEmpty()
        assertTrue("権限は一切宣言すべきではない。実際: ${requested.toList()}", requested.isEmpty())
    }
}
