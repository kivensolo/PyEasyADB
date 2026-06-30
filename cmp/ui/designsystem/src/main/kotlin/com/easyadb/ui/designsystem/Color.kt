package com.easyadb.ui.designsystem

import androidx.compose.ui.graphics.Color

/**
 * 日志级别颜色 —— 映射自 Python LogUtils.changeLogColor() in utils/Utils.py。
 */
object LogColor {
    val Debug = Color(0xFF388E3C)
    val Info = Color(0xFF263238)
    val Warn = Color(0xFFB07805)
    val Error = Color(0xFFBF360C)
    val Stamp = Color(0xFF005AC7)

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
 * 应用调色板 —— 从 Python QSS 样式中提取。
 */
object EasyAdbColors {
    // 主色
    val Primary = Color(0xFF1976D2)
    val PrimaryLight = Color(0xFF42A5F5)
    val PrimaryDark = Color(0xFF1565C0)

    // 表面与背景
    val Surface = Color(0xFFFFFFFF)
    val Background = Color(0xFFF5F5F5)
    val BackgroundDark = Color(0xFFE0E0E0)

    // 设备树
    val DeviceRootBg = Color(0xFFF0F0F0)
    val TreeItemSelected = Color(0xFF90CAF9)
    val TreeItemHover = Color(0xFFBBDEFB)

    // 输入框
    val InputBorder = Color(0xFFCCCCCC)
    val InputBorderHover = Color(0xFF83D4FC)
    val InputBg = Color(0xFFFFFFFF)

    // 按钮
    val ButtonHoverBg = Color(0xFFB3D7F3)
    val ButtonHoverBorder = Color(0xFF1E88DC)
    val ButtonPressedBg = Color(0xFF80BCEB)
    val ButtonPressedBorder = Color(0xFF2D8FDC)

    // 状态
    val StatusOnline = Color(0xFF4CAF50)
    val StatusOffline = Color(0xFF9E9E9E)
    val StatusUnauthorized = Color(0xFFFF9800)

    // 下拉框
    val ComboBoxBorder = Color(0xFFC4C4C4)
    val ComboBoxSelectedBorder = Color(0xFF2A89F6)
    val ComboBoxSelectedBg = Color(0xFF2A89F6)

    // 状态栏
    val StatusBarBg = Color(0xFFF2F2F2)

    // 文字
    val TextPrimary = Color(0xFF212121)
    val TextSecondary = Color(0xFF757575)
    val TextOnPrimary = Color(0xFFFFFFFF)

    // 对话框
    val DialogTitleBg = Color(0xFF000000)
    val DialogTitleText = Color(0xFFFFFFFF)
    val DialogBg = Color(0xFFFFFFFF)

    // 分割线
    val Divider = Color(0xFFD7D7D7)
}
