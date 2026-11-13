package darkdragon.AustinWu.gomoku.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7B4B2A),
    onPrimary = Color.White,
    secondary = Color(0xFF4E6E5A),
    background = Color(0xFFF6F1E7),
    surface = Color(0xFFFFFBF3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD9A066),
    onPrimary = Color(0xFF3A2415),
    secondary = Color(0xFF9FC3AC),
    background = Color(0xFF1B1815),
    surface = Color(0xFF241F1B),
)

@Composable
fun GoGameTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content,
    )
}
