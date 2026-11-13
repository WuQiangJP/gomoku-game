package darkdragon.AustinWu.gomoku

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import darkdragon.AustinWu.gomoku.ui.GameScreen
import darkdragon.AustinWu.gomoku.ui.GoGameTheme

/**
 * 唯一の Activity。Compose 画面を保持する。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GoGameTheme {
                GameScreen()
            }
        }
    }
}
