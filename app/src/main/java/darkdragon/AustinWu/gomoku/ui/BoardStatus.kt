package darkdragon.AustinWu.gomoku.ui

import darkdragon.AustinWu.gomoku.engine.Board

/**
 * 盤面のアクセシビリティ用の説明文：石の数と決着の有無。
 *
 * スクリーンリーダーと自動検証の双方がこの文字列に依存するため、直接単体テストできる
 * 純関数として切り出している。
 */
internal fun boardDescription(board: IntArray, winningLine: IntArray?): String {
    var black = 0
    var white = 0
    for (cell in board) {
        if (cell == Board.BLACK) {
            black++
        } else if (cell == Board.WHITE) {
            white++
        }
    }
    val text = StringBuilder("盤面、黒石 ")
    text.append(black).append(" 枚、白石 ").append(white).append(" 枚")
    if (winningLine != null && winningLine.size >= 5) {
        text.append("、決着がつきました")
    }
    return text.toString()
}
