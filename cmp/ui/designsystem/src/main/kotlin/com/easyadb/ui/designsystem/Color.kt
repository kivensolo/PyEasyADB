package com.easyadb.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * 日志级别颜色 —— 映射自 Python LogUtils.changeLogColor()。
 * 按 Material Design 规范微调色值，保持可辨识度。
 *
 * 注：日志级别色暂不参与明暗主题切换（暗色下 Info 深灰不可读等问题留待
 * 主题化专项处理），保持静态。
 */
object LogColor {
    val Debug = Color(0xFF2E7D32)   // Green 800
    val Info = Color(0xFF424242)     // Grey 800
    val Warn = Color(0xFFF57F17)     // Yellow 900
    val Error = Color(0xFFD32F2F)    // Red 700
    val Stamp = Color(0xFF1565C0)    // Blue 800

    fun byLevel(level: Int): Color = when (level) {
        0 -> Debug
        1 -> Debug
        2 -> Info
        3 -> Warn
        4 -> Error
        5 -> Stamp
        else -> Info
    }
}

/**
 * 应用扩展调色板（Material 主题槽位之外的颜色）。
 * 以不可变实例承载，通过 [LocalEasyAdbColors] 随主题分发，明暗各一套；
 * 运行时切换主题只需在 [EasyAdbTheme] 上层传入新的 darkTheme 状态即可整组生效。
 *
 * 属性名沿用 PascalCase（历史调用点迁移时仅需改接收者，不做属性重命名）。
 */
@Immutable
data class EasyAdbColors(
    // ── 主色 ──
    val Primary: Color,
    val PrimaryDark: Color,
    val PrimaryLight: Color,

    // ── 表面与背景 ──
    val Surface: Color,
    val Background: Color,

    // ── 状态颜色 ──
    val StatusOnline: Color,
    val StatusOffline: Color,
    val StatusUnauthorized: Color,

    // ── 交互状态底色（菜单项 / 列表行，全局统一） ──
    /** 鼠标悬浮底色，弱于选中态，形成 hover < selected 的视觉层次 */
    val Hover: Color,
    /** 选中底色 */
    val Selected: Color,

    // ── 文字 ──
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextOnPrimary: Color,

    // ── 面板底色（桌面低对比度场景） ──
    val SurfaceVariant: Color,
    val PanelHeader: Color,

    // ── 分割线 ──
    val Divider: Color
)

/** 浅色调色板（默认；取值为历史静态版本的原值，视觉零变化） */
val LightEasyAdbColors = EasyAdbColors(
    Primary = Color(0xFF1565C0),          // Blue 800
    PrimaryDark = Color(0xFF0D47A1),      // Blue 900
    PrimaryLight = Color(0xFF42A5F5),     // Blue 400
    Surface = Color(0xFFFFFFFF),
    Background = Color(0xFFFAFAFA),       // Grey 50
    StatusOnline = Color(0xFF2E7D32),     // Green 800
    StatusOffline = Color(0xFF757575),    // Grey 600
    StatusUnauthorized = Color(0xFFE65100), // Orange 900
    Hover = Color(0xFF1565C0).copy(alpha = 0.08f),   // 品牌蓝 8%
    Selected = Color(0xFF1565C0).copy(alpha = 0.18f), // 品牌蓝 18%
    TextPrimary = Color(0xFF212121),      // Grey 900
    TextSecondary = Color(0xFF757575),    // Grey 600
    TextOnPrimary = Color(0xFFFFFFFF),
    SurfaceVariant = Color(0xFFF5F5F5),   // Grey 100，用于侧栏/面板
    PanelHeader = Color(0xFFEEEEEE),      // Grey 200，用于表头/标签栏
    Divider = Color(0xFFE0E0E0)           // Grey 300
)

/**
 * 深色调色板（预置稿：表面/文字/分割线取深色系，主色与交互底色提亮加浓以保证可见度）。
 * 启用暗色主题前需人工校对各面板对比度。
 */
val DarkEasyAdbColors = EasyAdbColors(
    Primary = Color(0xFF42A5F5),          // Blue 400 提亮
    PrimaryDark = Color(0xFF1565C0),      // Blue 800
    PrimaryLight = Color(0xFF90CAF9),     // Blue 200
    Surface = Color(0xFF1E1E1E),
    Background = Color(0xFF121212),
    StatusOnline = Color(0xFF81C784),     // Green 300
    StatusOffline = Color(0xFF9E9E9E),    // Grey 500
    StatusUnauthorized = Color(0xFFFFB74D), // Orange 300
    Hover = Color(0xFF42A5F5).copy(alpha = 0.16f),
    Selected = Color(0xFF42A5F5).copy(alpha = 0.30f),
    TextPrimary = Color(0xFFE0E0E0),      // Grey 300
    TextSecondary = Color(0xFF9E9E9E),    // Grey 500
    TextOnPrimary = Color(0xFF0D47A1),    // 深底浅字反转为浅底深字
    SurfaceVariant = Color(0xFF2A2A2A),
    PanelHeader = Color(0xFF333333),
    Divider = Color(0xFF3C3C3C)
)

/** 当前组合中的扩展调色板，由 [EasyAdbTheme] 按明暗提供 */
val LocalEasyAdbColors = staticCompositionLocalOf { LightEasyAdbColors }

/** 便捷访问器：Composable 内直接 easyAdbColors.Hover / easyAdbColors.TextPrimary */
val easyAdbColors: EasyAdbColors
    @Composable get() = LocalEasyAdbColors.current
