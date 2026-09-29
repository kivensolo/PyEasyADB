package com.easyadb.ui.designsystem

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

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
    /** 提示/占位文字（弱于 TextSecondary，如输入框 placeholder、状态栏计数） */
    val TextHint: Color,
    /** 禁用文字/图标（如禁用按钮标签、占位工具按钮） */
    val TextDisabled: Color,

    // ── 面板底色（桌面低对比度场景） ──
    val SurfaceVariant: Color,
    val PanelHeader: Color,

    // ── 描边与分割 ──
    /** 输入框/控件描边 */
    val Border: Color,
    /** 分割线、卡片描边 */
    val Divider: Color,

    // ── 反色浮层（Tooltip：亮主题深气泡、暗主题浅气泡） ──
    val TooltipBackground: Color,
    val TooltipText: Color,

    // ── 实时日志 Find 工具条命中高亮 ──
    /** 普通命中底色（浅色主题配深字、暗色主题配浅字，两套取值不同） */
    val FindHighlight: Color,
    /** 当前命中底色（更强一级） */
    val FindCurrent: Color,

    // ── 日志级别色（P11 主题化：替代原静态 LogColor，暗色下整组提亮） ──
    val LogDebug: Color,
    val LogInfo: Color,
    val LogWarn: Color,
    val LogError: Color,
    val LogStamp: Color
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
    TextHint = Color(0xFF9E9E9E),         // Grey 500
    TextDisabled = Color(0xFFBDBDBD),     // Grey 400
    SurfaceVariant = Color(0xFFF5F5F5),   // Grey 100，用于侧栏/面板
    PanelHeader = Color(0xFFEEEEEE),      // Grey 200，用于表头/标签栏
    Border = Color(0xFFBDBDBD),           // Grey 400
    Divider = Color(0xFFE0E0E0),          // Grey 300
    TooltipBackground = Color(0xFF424242), // Grey 800，深气泡
    TooltipText = Color(0xFFFFFFFF),
    FindHighlight = Color(0xFFFFE082),    // Amber 200，浅黄底配深字
    FindCurrent = Color(0xFFFFB300),      // Amber 600，当前命中
    LogDebug = Color(0xFF2E7D32),         // Green 800（原 LogColor.Debug）
    LogInfo = Color(0xFF424242),          // Grey 800（原 LogColor.Info）
    LogWarn = Color(0xFFF57F17),          // Yellow 900（原 LogColor.Warn）
    LogError = Color(0xFFD32F2F),         // Red 700（原 LogColor.Error）
    LogStamp = Color(0xFF1565C0)          // Blue 800（原 LogColor.Stamp）
)

/**
 * 深色调色板（P11 人工校对稿）：
 * - 表面层级 Background(121212) < Surface(1E1E1E) < SurfaceVariant(2A2A2A) < PanelHeader(333333)，
 *   对齐 Material 暗色"越浮起越亮"的层级规则；
 * - 文字 TextPrimary(E0E0E0)/TextSecondary(9E9E9E)/TextHint(7A7A7A)/TextDisabled(5A5A5A)
 *   在 1E1E1E 上对比度依次 12.6 / 4.7 / 2.9 / 1.8，仅装饰性元素用后两档；
 * - 描边/分割线取 4A4A4A / 3C3C3C，在两档表面间可辨但不抢眼；
 * - 状态色与日志级别色整体提亮一档（Material 200/300 系），Warn/Error 等
 *   高饱和色降到 300 而非 200，避免暗底上过曝刺眼；
 * - Find 命中高亮反转为暗琥珀/暗橄榄（浅字可读），不再是浅色主题的亮黄底。
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
    TextHint = Color(0xFF7A7A7A),
    TextDisabled = Color(0xFF5A5A5A),
    SurfaceVariant = Color(0xFF2A2A2A),
    PanelHeader = Color(0xFF333333),
    Border = Color(0xFF4A4A4A),
    Divider = Color(0xFF3C3C3C),
    TooltipBackground = Color(0xFFE0E0E0), // 浅气泡（与亮主题反转）
    TooltipText = Color(0xFF212121),
    FindHighlight = Color(0xFF584A1A),    // 暗橄榄，浅字可读
    FindCurrent = Color(0xFF8C6D00),      // 暗琥珀，当前命中
    LogDebug = Color(0xFF81C784),         // Green 300
    LogInfo = Color(0xFFCCCCCC),
    LogWarn = Color(0xFFFFD54F),          // Yellow 300
    LogError = Color(0xFFE57373),         // Red 300
    LogStamp = Color(0xFF64B5F6)          // Blue 300
)

/** 当前组合中的扩展调色板，由 [EasyAdbTheme] 按明暗提供 */
val LocalEasyAdbColors = staticCompositionLocalOf { LightEasyAdbColors }

/** 便捷访问器：Composable 内直接 easyAdbColors.Hover / easyAdbColors.TextPrimary */
val easyAdbColors: EasyAdbColors
    @Composable get() = LocalEasyAdbColors.current

/**
 * 日志级别 → 主题化颜色。替代原静态 `LogColor.byLevel`（浅色取值与原值一致，
 * 暗色下整组提亮：Info 深灰在暗底不可读的问题随主题化解决）。
 *
 * level 约定沿用 Python 版：0/1=DEBUG、2=INFO、3=WARN、4=ERROR、5=STAMP。
 */
@Composable
fun logColor(level: Int): Color {
    val c = easyAdbColors
    return when (level) {
        0, 1 -> c.LogDebug
        2 -> c.LogInfo
        3 -> c.LogWarn
        4 -> c.LogError
        5 -> c.LogStamp
        else -> c.LogInfo
    }
}
