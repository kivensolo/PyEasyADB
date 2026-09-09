package com.easyadb.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/** 由扩展调色板实例构建 Material 浅色槽位（工厂函数，避免顶层 val 引用静态色） */
private fun lightPalette(c: EasyAdbColors) = lightColors(
    primary = c.Primary,
    primaryVariant = c.PrimaryDark,
    secondary = c.PrimaryLight,
    background = c.Background,
    surface = c.Surface,
    error = LogColor.Error,
    onPrimary = c.TextOnPrimary,
    onSecondary = c.TextPrimary,
    onBackground = c.TextPrimary,
    onSurface = c.TextPrimary,
    onError = c.TextOnPrimary
)

/** 由扩展调色板实例构建 Material 深色槽位 */
private fun darkPalette(c: EasyAdbColors) = darkColors(
    primary = c.Primary,
    primaryVariant = c.PrimaryDark,
    secondary = c.PrimaryLight,
    background = c.Background,
    surface = c.Surface,
    error = LogColor.Error,
    onPrimary = Color(0xFF0D47A1),
    onSecondary = c.TextOnPrimary,
    onBackground = c.TextPrimary,
    onSurface = c.TextPrimary,
    onError = c.TextOnPrimary
)

/**
 * EasyADB 主题组件。
 * 包装 MaterialTheme，集成应用特定的颜色和排版，并通过 [LocalEasyAdbColors]
 * 向下分发扩展调色板（明暗各一套）。
 *
 * 主题切换预留接口：darkTheme 为普通参数，上层持 `mutableStateOf` 传入即可
 * 运行时全局切换（Material 槽位与扩展色整组生效）。
 */
@Composable
fun EasyAdbTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val extended = if (darkTheme) DarkEasyAdbColors else LightEasyAdbColors
    val colors = if (darkTheme) darkPalette(extended) else lightPalette(extended)

    CompositionLocalProvider(LocalEasyAdbColors provides extended) {
        MaterialTheme(
            colors = colors,
            typography = EasyAdbTypography,
            content = content
        )
    }
}
