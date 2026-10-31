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

/**
 * 默认 UI 字体：Windows 显式加载微软雅黑字体文件（走查反馈：中文按钮/标签发虚）。
 *
 * 坑（PlatformFont.skiko.kt GenericFontFamiliesMapping）：`FontFamily.SansSerif`
 * 在 Windows 映射 `["Segoe UI", "Arial"]`——两者均**不含中文字形**，中文经 Skia
 * 回退链落到宋体（SimSun），小字号笔画细、无次像素渲染，视觉发虚；注释里
 * 「SansSerif → 微软雅黑」的旧假设不成立。
 *
 * 注意必须走**字体文件**（`Font(File)`）而非族名 `FontFamily("Microsoft YaHei
 * UI")`：实测族名路径在 Skia 的 FontMgr 里未解析到雅黑，中文仍落宋体发虚；
 * 文件加载与 Consolas token 同机制（已验证有效），makeFromFile 对 .ttc 集合
 * 取 face 0（msyh.ttc=YaHei UI 常规 / msyhbd.ttc=YaHei UI 粗体）。文件缺失
 * （非 Windows/精简系统）回退 SansSerif。
 */
private fun windowsUiFontFamily(): FontFamily {
    val fontsDir = java.io.File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts")
    val yahei = java.io.File(fontsDir, "msyh.ttc")
    val yaheiBold = java.io.File(fontsDir, "msyhbd.ttc")
    if (yahei.isFile) {
        return FontFamily(
            androidx.compose.ui.text.platform.Font(yahei, FontWeight.Normal),
            if (yaheiBold.isFile) {
                androidx.compose.ui.text.platform.Font(yaheiBold, FontWeight.Bold)
            } else {
                androidx.compose.ui.text.platform.Font(yahei, FontWeight.Bold)
            }
        )
    }
    return FontFamily.SansSerif
}

/** 默认 UI 字体：按平台选择合适无衬线字体 */
val defaultUiFontFamily: FontFamily = when {
    osName.contains("win") -> windowsUiFontFamily()  // Windows → 微软雅黑（显式加载）
    osName.contains("mac") -> FontFamily.SansSerif  // macOS → 苹方
    else -> FontFamily.SansSerif                     // Linux → Noto Sans CJK SC
}

/**
 * 等宽字体：Consolas（SP-2003 调整5 决策）。
 *
 * FontFamily.Monospace 在 Windows 落到 Courier New——笔画极细 + 灰度抗锯齿，
 * 小字号（10.5–12.5sp）下发虚；Consolas 专为屏显设计，清晰度与微软雅黑同级且
 * 仍是等宽。从系统字体文件加载（consola.ttf 常规体 / consolab.ttf 粗体），
 * 文件缺失（非 Windows/精简系统）回退 FontFamily.Monospace。
 */
val easyAdbMonospace: FontFamily = run {
    val consolas = java.io.File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts/consola.ttf")
    val consolasBold = java.io.File(System.getenv("WINDIR") ?: "C:\\Windows", "Fonts/consolab.ttf")
    if (consolas.isFile && consolasBold.isFile) {
        FontFamily(
            androidx.compose.ui.text.platform.Font(consolas, FontWeight.Normal),
            androidx.compose.ui.text.platform.Font(consolasBold, FontWeight.Bold)
        )
    } else {
        FontFamily.Monospace
    }
}

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
