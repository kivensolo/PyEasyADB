package com.easyadb.ui.designsystem

import androidx.compose.material.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 平台感知字体族。
 * 映射自 Python Tools.py 字体函数：
 *   getWRYHFontStyle()   → 微软雅黑（粗体，用于标签）
 *   globalEditTextFontStyle() → SansSerif（用于编辑文本）
 *   getSongFontStyle()   → 宋体 / serif 回退
 *   getSimpleFontStyle() → 微软雅黑 Light
 */

private val osName: String = System.getProperty("os.name").lowercase()

/** 默认 UI 字体：按平台选择合适无衬线字体 */
val defaultUiFontFamily: FontFamily = when {
    osName.contains("win") -> FontFamily.SansSerif  // Windows → 微软雅黑
    osName.contains("mac") -> FontFamily.SansSerif  // macOS → 苹方
    else -> FontFamily.SansSerif                     // Linux → Noto Sans CJK SC
}

/** 等宽字体，用于控制台/logcat */
val monospaceFontFamily: FontFamily = FontFamily.Monospace

/**
 * EasyADB 排版系统。
 * 基准字号来自 Python：标签=10sp，编辑框=13sp，按钮=10sp，标题=26sp
 */
val EasyAdbTypography = Typography(
    h1 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp  // 对话框标题（来自 AboutDialog）
    ),
    h2 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp
    ),
    h3 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp
    ),
    body1 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp  // 编辑框文本（来自 globalEditTextFontStyle）
    ),
    body2 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp  // 次要信息
    ),
    subtitle1 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp  // 分组框标签（来自 getSimpleFontStyle/getWRYHFontStyle）
    ),
    subtitle2 = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp
    ),
    caption = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 9.sp   // 自定义控件（来自 CustomWidgets.py）
    ),
    button = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 10.sp  // 工具按钮（来自 getWRYHFontStyle）
    ),
    overline = TextStyle(
        fontFamily = defaultUiFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 8.sp   // 构建信息（来自 AboutDialog）
    )
)
