package com.easyadb.ui.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Shapes
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** 由扩展调色板实例构建 Material 浅色槽位（工厂函数，避免顶层 val 引用静态色） */
private fun lightPalette(c: EasyAdbColors) = lightColors(
    primary = c.Primary,
    primaryVariant = c.PrimaryDark,
    secondary = c.PrimaryLight,
    background = c.Background,
    surface = c.Surface,
    error = c.LogError,
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
    error = c.LogError,
    onPrimary = c.TextOnPrimary,
    onSecondary = c.TextPrimary,
    onBackground = c.TextPrimary,
    onSurface = c.TextPrimary,
    onError = c.TextOnPrimary
)

/**
 * 主题三态选择。配置项 `[General] Theme` 持小写值 `system|light|dark`；
 * 菜单「工具」下提供三项切换（当前态加 ✓ 前缀），选择即写回配置。
 */
enum class ThemeMode {
    /** 跟随系统（`isSystemInDarkTheme()`），默认值 */
    SYSTEM,
    LIGHT,
    DARK;

    /** 配置文件中的小写取值 */
    val configValue: String get() = name.lowercase()

    companion object {
        /** 从配置值解析，无效/缺省回落 [SYSTEM] */
        fun fromConfigValue(value: String): ThemeMode =
            entries.firstOrNull { it.configValue == value.trim().lowercase() } ?: SYSTEM
    }
}

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
    val tokens = EasyAdbTokensDefault

    CompositionLocalProvider(
        LocalEasyAdbColors provides extended,
        LocalEasyAdbTokens provides tokens
    ) {
        MaterialTheme(
            colors = colors,
            typography = EasyAdbTypography,
            // Material 语义圆角映射到 token 体系：未翻新的 Material 组件自动收敛
            shapes = Shapes(
                small = tokens.shapeSm,
                medium = tokens.shapeLg,
                large = tokens.shapeXl
            ),
            content = content
        )
    }
}
