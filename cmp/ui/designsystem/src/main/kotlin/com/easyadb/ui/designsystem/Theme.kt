package com.easyadb.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColorPalette = lightColors(
    primary = EasyAdbColors.Primary,
    primaryVariant = EasyAdbColors.PrimaryDark,
    secondary = EasyAdbColors.PrimaryLight,
    background = EasyAdbColors.Background,
    surface = EasyAdbColors.Surface,
    error = LogColor.Error,
    onPrimary = EasyAdbColors.TextOnPrimary,
    onSecondary = EasyAdbColors.TextPrimary,
    onBackground = EasyAdbColors.TextPrimary,
    onSurface = EasyAdbColors.TextPrimary,
    onError = EasyAdbColors.TextOnPrimary
)

private val DarkColorPalette = darkColors(
    primary = EasyAdbColors.PrimaryLight,
    primaryVariant = EasyAdbColors.Primary,
    secondary = EasyAdbColors.PrimaryLight,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    error = LogColor.Error,
    onPrimary = EasyAdbColors.TextOnPrimary,
    onSecondary = EasyAdbColors.TextOnPrimary,
    onBackground = Color(0xFFE0E0E0),
    onSurface = Color(0xFFE0E0E0),
    onError = EasyAdbColors.TextOnPrimary
)

/**
 * EasyADB 主题组件。
 * 包装 MaterialTheme，集成应用特定的颜色和排版。
 */
@Composable
fun EasyAdbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkColorPalette else LightColorPalette

    MaterialTheme(
        colors = colors,
        typography = EasyAdbTypography,
        content = content
    )
}