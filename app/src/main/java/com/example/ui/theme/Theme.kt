package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = BentoActionBg,
    onPrimary = Color.White,
    secondary = BentoMainStatCardBg,
    onSecondary = Color(0xFF001D35),
    tertiary = BentoScriptCardBg,
    onTertiary = BentoScriptText,
    background = BentoBg,
    onBackground = BentoTextPrimary,
    surface = BentoCardWhiteBg,
    onSurface = BentoTextPrimary,
    surfaceVariant = Color(0xFFE1E2EC),
    onSurfaceVariant = BentoTextSecondary,
    outline = BentoBorder,
    error = TerminalRubyRed,
    onError = Color.White
)

private val DarkColorScheme = darkColorScheme(
    primary = Color.White,
    onPrimary = BentoActionBg,
    secondary = Color(0xFF2D324A),
    onSecondary = Color(0xFFDDE2F9),
    tertiary = Color(0xFF322C42),
    onTertiary = Color(0xFFE8DEF8),
    background = Color(0xFF131317),
    onBackground = Color(0xFFE3E2E6),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE3E2E6),
    surfaceVariant = Color(0xFF2D2F36),
    onSurfaceVariant = Color(0xFFC3C6CF),
    outline = Color(0xFF43454E),
    error = TerminalRubyRed,
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false, // Set to false so the gorgeous Bento Light theme is default!
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme


    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
