package dev.smto.driveassistant.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val Dark = darkColorScheme(
    primary = Color(0xFF7FB2FF),
    secondary = Color(0xFF9FD8C0),
    background = Color(0xFF101216),
    surface = Color(0xFF181B20),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0B5FD0),
    secondary = Color(0xFF2E7D64),
    background = Color(0xFFF7F8FA),
    surface = Color(0xFFFFFFFF),
)

@Composable
fun DriveAssistantTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) Dark else Light
    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as Activity).window
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
    }
    MaterialTheme(colorScheme = colors, content = content)
}
