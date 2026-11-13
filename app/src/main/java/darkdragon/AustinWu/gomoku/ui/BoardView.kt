package darkdragon.AustinWu.gomoku.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import darkdragon.AustinWu.gomoku.engine.Board
import darkdragon.AustinWu.gomoku.engine.Board.Companion.BLACK

/**
 * 盤面の描画コンポーネント。
 *
 * 盤自体は Canvas で描き、タッチイベントから最寄りの交点へ座標を変換して着手する。
 */
@Composable
fun BoardView(
    board: IntArray,
    size: Int,
    lastMove: Int,
    winningLine: IntArray?,
    enabled: Boolean,
    onCellClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val gridColor = Color(0xFF8A6A4B)
    val blackStone = Color(0xFF1C1A18)
    val whiteStone = Color(0xFFF7F3EA)
    val markerColor = Color(0xFFD4453C)

    // アクセシビリティサービスと自動テストが読み取る盤面の状態説明
    val description = boardDescription(board, winningLine)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(12.dp)
            .background(Color(0xFFE8CFA8), RoundedCornerShape(8.dp))
            .semantics { contentDescription = description }
            .pointerInput(enabled, size, board) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    // 下の描画とまったく同じ幾何を使う：四方に 1 目分の余白を取る
                    val boardPadding = this.size.width / (size + 1f)
                    val cell = (this.size.width - boardPadding * 2) / (size - 1f)
                    val col = Math.round((offset.x - boardPadding) / cell).toInt()
                    val row = Math.round((offset.y - boardPadding) / cell).toInt()
                    if (row in 0 until size && col in 0 until size) {
                        onCellClick(row * size + col)
                    }
                }
            },
    ) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(1f)) {
            val padding = this.size.width / (size + 1f)
            val cell = (this.size.width - padding * 2) / (size - 1f)

            // 格子線
            for (i in 0 until size) {
                val pos = padding + cell * i
                drawLine(
                    color = gridColor,
                    start = Offset(padding, pos),
                    end = Offset(this.size.width - padding, pos),
                    strokeWidth = 1.5f,
                )
                drawLine(
                    color = gridColor,
                    start = Offset(pos, padding),
                    end = Offset(pos, this.size.height - padding),
                    strokeWidth = 1.5f,
                )
            }

            // 星位
            val stars = listOf(3, 7, 11)
            for (r in stars) {
                for (c in stars) {
                    drawCircle(
                        color = gridColor,
                        radius = 3.5f,
                        center = Offset(padding + cell * c, padding + cell * r),
                    )
                }
            }

            // 石
            for (index in board.indices) {
                val color = board[index]
                if (color == Board.EMPTY) continue
                val row = index / size
                val col = index % size
                val center = Offset(padding + cell * col, padding + cell * row)
                val radius = cell * 0.42f
                drawCircle(
                    color = if (color == BLACK) blackStone else whiteStone,
                    radius = radius,
                    center = center,
                )
                drawCircle(
                    color = Color.Black.copy(alpha = 0.25f),
                    radius = radius,
                    center = center,
                    style = Stroke(width = 1.5f),
                )
            }

            // 直近の一手のマーク
            if (lastMove in board.indices) {
                val row = lastMove / size
                val col = lastMove % size
                drawCircle(
                    color = markerColor,
                    radius = cell * 0.13f,
                    center = Offset(padding + cell * col, padding + cell * row),
                )
            }

            // 決め手の五連をハイライト
            if (winningLine != null && winningLine.size >= 5) {
                val first = winningLine.first()
                val last = winningLine.last()
                drawLine(
                    color = markerColor,
                    start = Offset(
                        padding + cell * (first % size),
                        padding + cell * (first / size),
                    ),
                    end = Offset(
                        padding + cell * (last % size),
                        padding + cell * (last / size),
                    ),
                    strokeWidth = 6f,
                )
            }
        }
    }
}
