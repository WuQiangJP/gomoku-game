package darkdragon.AustinWu.gomoku.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import darkdragon.AustinWu.gomoku.engine.Board
import darkdragon.AustinWu.gomoku.engine.Board.Companion.BLACK
import darkdragon.AustinWu.gomoku.engine.Board.Companion.WHITE
import darkdragon.AustinWu.gomoku.game.Difficulty
import darkdragon.AustinWu.gomoku.game.GameUiState
import darkdragon.AustinWu.gomoku.game.GameViewModel
import darkdragon.AustinWu.gomoku.game.GameState

/**
 * メイン画面。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(viewModel: GameViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("五目並べ") },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            StatusBar(state)
            // 盤面は残りの高さを使う。画面が低いときは盤が縮み、操作ボタンは常に最初の画面に残る
            BoxWithConstraints(
                modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                val side = minOf(maxWidth, maxHeight)
                BoardView(
                    board = state.board,
                    size = Board.DEFAULT_SIZE,
                    lastMove = state.lastMove,
                    winningLine = state.winningLine,
                    enabled = !state.aiThinking && state.status == GameState.Status.PLAYING,
                    onCellClick = viewModel::onCellClicked,
                    modifier = Modifier.size(side).testTag("board"),
                )
            }
            Controls(state, viewModel)
        }
    }
}

@Composable
private fun StatusBar(state: GameUiState) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = state.message,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("status"),
            )
            Text(
                text = "難易度：${state.difficulty.displayName}　${if (state.humanColor == BLACK) "黒" else "白"}を持つ　" +
                    "第 ${state.moveHistory.size} 手",
                style = MaterialTheme.typography.bodySmall,
            )
            if (state.aiThinking) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.testTag("thinking").padding(2.dp),
                        strokeWidth = 2.dp,
                    )
                    Text("AI が思考中…", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun Controls(state: GameUiState, viewModel: GameViewModel) {
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.testTag("difficulty"),
            ) {
                Text("難易度：${state.difficulty.displayName}")
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                Difficulty.entries.forEach { difficulty ->
                    DropdownMenuItem(
                        text = { Text(difficulty.displayName) },
                        onClick = {
                            viewModel.setDifficulty(difficulty)
                            expanded = false
                        },
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Button(
                onClick = viewModel::restart,
                modifier = Modifier.weight(1f).testTag("restart"),
            ) {
                Text("新規対局")
            }
            OutlinedButton(
                onClick = viewModel::undo,
                enabled = state.moveHistory.isNotEmpty(),
                modifier = Modifier.weight(1f).testTag("undo"),
            ) {
                Text("待った")
            }
        }

        OutlinedButton(
            onClick = viewModel::swapSides,
            modifier = Modifier.fillMaxWidth().testTag("swap"),
        ) {
            Text("色を入れ替える（${if (state.humanColor == BLACK) "白" else "黒"}にする）")
        }
    }
}
