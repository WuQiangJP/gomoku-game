package darkdragon.AustinWu.gomoku.game

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import darkdragon.AustinWu.gomoku.data.GameStore
import darkdragon.AustinWu.gomoku.engine.Board
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 対局フローのテスト：先手の色、待ったの意味、復元後に AI の手番となる局面をカバーする。
 *
 * AI は「最初の空点」を返す決定的な実装に差し替え、フローを再現可能に進められるようにする。
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class GameViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        GameStore(ApplicationProvider.getApplicationContext<Application>()).clear()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** 実際の探索を「最初の空点」に差し替え、対局フローを決定的に進められるようにする。 */
    class StubViewModel(
        application: Application,
        private val dispatcher: CoroutineDispatcher,
    ) : GameViewModel(application) {

        override val aiDispatcher: CoroutineDispatcher
            get() = dispatcher

        override fun computeAiMove(
            cells: IntArray,
            side: Int,
            difficulty: Difficulty,
            isCancelled: () -> Boolean,
        ): Int = cells.indexOfFirst { it == Board.EMPTY }
    }

    fun createViewModel(): StubViewModel =
        StubViewModel(ApplicationProvider.getApplicationContext(), testDispatcher)

    fun settle() = testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `黒を持つ対局はプレイヤーが先に打つ`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        settle()
        val state = viewModel.uiState.value
        assertEquals(Board.BLACK, state.humanColor)
        assertEquals(Board.BLACK, state.currentPlayer)
        assertTrue("開始時に石があってはならない", state.moveHistory.isEmpty())
    }

    @Test
    fun `色を入れ替えても黒が先手`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.swapSides()
        settle()

        val state = viewModel.uiState.value
        assertEquals(Board.WHITE, state.humanColor)
        assertEquals("黒が先に一手打つべき", 1, state.moveHistory.size)
        assertEquals(Board.BLACK, state.board[state.moveHistory.first()])
        assertEquals("着手後は白を持つプレイヤーの手番", Board.WHITE, state.currentPlayer)
    }

    @Test
    fun `黒を持つ場合の待ったはプレイヤーの手番に戻る`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onCellClicked(0)
        settle()
        assertEquals(2, viewModel.uiState.value.moveHistory.size)

        viewModel.undo()
        settle()
        assertTrue("プレイヤーの一手と AI の受けを取り消すべき", viewModel.uiState.value.moveHistory.isEmpty())
        assertEquals(Board.BLACK, viewModel.uiState.value.currentPlayer)
    }

    @Test
    fun `白を持つ場合の待ったはプレイヤーの手番に戻る`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.swapSides()
        settle()
        assertEquals(1, viewModel.uiState.value.moveHistory.size)

        viewModel.onCellClicked(1)
        settle()
        assertEquals(3, viewModel.uiState.value.moveHistory.size)
        assertEquals(Board.WHITE, viewModel.uiState.value.currentPlayer)

        viewModel.undo()
        settle()
        assertEquals("プレイヤーの一手と AI の受けを取り消すべき", 1, viewModel.uiState.value.moveHistory.size)
        assertEquals("待ったの後は必ずプレイヤーの手番", Board.WHITE, viewModel.uiState.value.currentPlayer)
    }

    @Test
    fun `新規対局後は黒が先手で盤が空`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.swapSides()
        settle()
        viewModel.restart()
        settle()

        val state = viewModel.uiState.value
        assertEquals("黒が先に一手打つべき", 1, state.moveHistory.size)
        assertEquals(Board.BLACK, state.board[state.moveHistory.first()])
        assertEquals(Board.WHITE, state.currentPlayer)
    }

    @Test
    fun `思考中にやり直しても着手が衝突しない`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.swapSides()
        // AI がまだ打たないうちにただちにやり直す。旧ジョブがキャンセルされ、
        // 盤には黒石が 1 枚だけ残るはず
        viewModel.restart()
        settle()

        val state = viewModel.uiState.value
        assertEquals(1, state.moveHistory.size)
        assertEquals(Board.BLACK, state.board[state.moveHistory.first()])
        assertTrue(!state.aiThinking)
    }

    @Test
    fun `セーブデータ復元時に AI の手番なら自動で打つ`() = runTest(testDispatcher) {
        val store = GameStore(ApplicationProvider.getApplicationContext<Application>())
        store.saveHumanColor(Board.WHITE)
        store.saveMoves(listOf(0, 1)) // 黒・白が各 1 手。次は黒（AI）の手番

        val viewModel = createViewModel()
        settle()

        val state = viewModel.uiState.value
        assertEquals("復元後に AI が自動で一手補うべき", 3, state.moveHistory.size)
        assertEquals(Board.WHITE, state.currentPlayer)
    }
}
