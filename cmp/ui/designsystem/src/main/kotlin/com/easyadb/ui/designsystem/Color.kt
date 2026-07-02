package com.easyadb.ui.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 日志级别颜色 —— 映射自 Python LogUtils.changeLogColor()。
 * 按 Material Design 规范微调色值，保持可辨识度。
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
 * Material Design 调色板。
 * 按桌面工具类应用的风格配色，移除 PyQt QSS 遗留色值。
 */
object EasyAdbColors {
    // ── 主色 ──
    val Primary = Color(0xFF1565C0)         // Blue 800
    val PrimaryDark = Color(0xFF0D47A1)     // Blue 900
    val PrimaryLight = Color(0xFF42A5F5)    // Blue 400

    // ── 表面与背景 ──
    val Surface = Color(0xFFFFFFFF)
    val Background = Color(0xFFFAFAFA)      // Grey 50

    // ── 状态颜色 ──
    val StatusOnline = Color(0xFF2E7D32)    // Green 800
    val StatusOffline = Color(0xFF757575)   // Grey 600
    val StatusUnauthorized = Color(0xFFE65100) // Orange 900

    // ── 文字 ──
    val TextPrimary = Color(0xFF212121)     // Grey 900
    val TextSecondary = Color(0xFF757575)   // Grey 600
    val TextOnPrimary = Color(0xFFFFFFFF)

    // ── 面板底色（桌面低对比度场景） ──
    val SurfaceVariant = Color(0xFFF5F5F5)  // Grey 100，用于侧栏/面板
    val PanelHeader = Color(0xFFEEEEEE)     // Grey 200，用于表头/标签栏

    // ── 分割线 ──
    val Divider = Color(0xFFE0E0E0)         // Grey 300
}