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
 * 取值对齐 HTML 参照稿（easyadb-ui/index.html）明暗双主题 CSS 变量：
 * - 暗色取 `:root`（深空蓝黑系）、浅色取 `html[data-theme="light"]`；
 * - 各字段注释标注对应 HTML 变量名/选择器，散落在类选择器中的硬编码色
 *   （.dev-card.sel / .wc:hover / .pfield input 等）一并归位；
 * - HTML 未提供浅色稿的色（a-icon 语义色、Find 高亮）按暗色值在白底上的
 *   可读性降饱和推导，注释中标注「推导」。
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

    // ── seg 分组按钮（常用命令工具条 .seg，H5 明暗各有字面覆盖色） ──
    /** 分组容器底（H5 .seg：暗 = --field #0d1524 / 浅 = #eaeff8 覆盖） */
    val SegTrack: Color,
    /** 选中分组项底色（H5 .seg button.active：暗 #22335a / 浅 #ffffff） */
    val SegActiveBg: Color,
    /** 选中分组项文字（H5 .seg button.active：暗 #fff / 浅 #1d4ed8） */
    val SegActiveText: Color,

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

    // ── 功能卡图标语义色（HTML .a-icon.{green|red|blue|orange|gray|sky|rose}）──
    val AccentGreen: Color,
    val AccentRed: Color,
    val AccentBlue: Color,
    val AccentOrange: Color,
    val AccentGrey: Color,
    val AccentSky: Color,
    val AccentRose: Color,

    // ── 反色浮层（Tooltip：亮主题深气泡、暗主题浅气泡） ──
    val TooltipBackground: Color,
    val TooltipText: Color,

    // ── 实时日志 Find 工具条命中高亮 ──
    /** 普通命中底色（浅色主题配深字、暗色主题配浅字，两套取值不同） */
    val FindHighlight: Color,
    /** 当前命中底色（更强一级） */
    val FindCurrent: Color,

    // ── 日志级别色（对齐 HTML 控制台 .lv-* 行级配色） ──
    val LogDebug: Color,
    val LogInfo: Color,
    val LogWarn: Color,
    val LogError: Color,
    val LogStamp: Color
)

/**
 * 浅色调色板：对齐 HTML 参照稿 `html[data-theme="light"]`。
 * 基准：win-bg #F6F8FC 窗底、panel/card #FFFFFF 面板、tx #1B2534 主文字、
 * 蓝系强调 #2563EB（btn 渐变深端）；状态色取浅色变量 --green/--red/--amber/--cyan。
 */
val LightEasyAdbColors = EasyAdbColors(
    Primary = Color(0xFF2563EB),          // btn 渐变深端 / 浅色强调蓝
    PrimaryDark = Color(0xFF1D4ED8),      // 浅色文字级强调蓝（combo/cmd-line hover）
    PrimaryLight = Color(0xFF3B82F6),     // --blue（a-icon.blue 浅色推导）
    Surface = Color(0xFFFFFFFF),          // --panel / --card-hi
    Background = Color(0xFFF6F8FC),       // --win-bg
    StatusOnline = Color(0xFF16A34A),     // --green
    StatusOffline = Color(0xFF93A1B5),    // --tx-3
    StatusUnauthorized = Color(0xFFB45309), // --amber
    Hover = Color(0xFFE6EDF7),            // .wc:hover
    Selected = Color(0xFFE9F1FE),         // .dev-card.sel 渐变底（#eff5ff→#e9f1fe）
    SegTrack = Color(0xFFEAEFF8),         // .seg 容器底（浅色覆盖）
    SegActiveBg = Color(0xFFFFFFFF),      // .seg button.active 白底
    SegActiveText = Color(0xFF1D4ED8),    // .seg button.active 蓝字
    TextPrimary = Color(0xFF1B2534),      // --tx
    TextSecondary = Color(0xFF5A6B82),    // --tx-2
    TextOnPrimary = Color(0xFFFFFFFF),
    TextHint = Color(0xFFA3B1C4),         // input::placeholder
    TextDisabled = Color(0xFF93A1B5),     // --tx-3
    SurfaceVariant = Color(0xFFF2F5FA),   // .sidebar
    PanelHeader = Color(0xFFF3F6FB),      // .con-head / .tabs
    Border = Color(0xFFC9D6EA),           // .pfield input 描边（浅）
    Divider = Color(0xFFEBEDF1),          // --line 合成（rgba(15,23,42,.08) 于白底）
    AccentGreen = Color(0xFF16A34A),      // --green（a-icon 浅色推导）
    AccentRed = Color(0xFFDC2626),        // --red
    AccentBlue = Color(0xFF2563EB),       // blue-600（a-icon.blue 浅色推导）
    AccentOrange = Color(0xFFEA580C),     // orange-600（a-icon.orange 浅色推导）
    AccentGrey = Color(0xFF64748B),       // slate-500（a-icon.gray 浅色推导）
    AccentSky = Color(0xFF0369A1),        // --cyan
    AccentRose = Color(0xFFE11D48),       // rose-600（a-icon.rose 浅色推导）
    TooltipBackground = Color(0xFFEEF3F9), // 浅色气泡（微蓝灰，比 Surface 深一档保辨识）
    TooltipText = Color(0xFF1B2534),
    FindHighlight = Color(0xFFFFE082),    // Amber 200，浅黄底配深字（HTML 无对应，保留原值）
    FindCurrent = Color(0xFFFFB300),      // Amber 600，当前命中（同上）
    LogDebug = Color(0xFF93A1B5),         // .lv-dim
    LogInfo = Color(0xFF334155),          // .lv-info
    LogWarn = Color(0xFFB45309),          // .lv-warn
    LogError = Color(0xFFDC2626),         // .lv-err
    LogStamp = Color(0xFF1D4ED8)          // 浅色强调蓝（命令回显标记）
)

/**
 * 深色调色板：对齐 HTML 参照稿 `:root`（深空蓝黑系）。
 * 基准：win-bg #0C1320 窗底、panel #101A2B 面板、tx #E6ECF6 主文字、
 * --blue #3B82F6 主色；交互底取 .wc:hover / .dev-card.sel 等硬编码层级色。
 */
val DarkEasyAdbColors = EasyAdbColors(
    Primary = Color(0xFF3B82F6),          // --blue
    PrimaryDark = Color(0xFF2563EB),      // --blue-deep
    PrimaryLight = Color(0xFF60A5FA),     // a-icon.blue（.cur-dev svg 同源）
    Surface = Color(0xFF101A2B),          // --panel（card 渐变基线 #111a2b）
    Background = Color(0xFF0C1320),       // --win-bg
    StatusOnline = Color(0xFF4ADE80),     // --green
    StatusOffline = Color(0xFF5F7089),    // --tx-3（.dev-state.off）
    StatusUnauthorized = Color(0xFFFBBF24), // --amber（.dev-state.busy）
    Hover = Color(0xFF1A2740),            // .wc:hover / .frow:hover 同级
    Selected = Color(0xFF1B2A47),         // .dev-card.sel 渐变底（#17253e→#132036）
    SegTrack = Color(0xFF0D1524),         // .seg 容器底（--field）
    SegActiveBg = Color(0xFF22335A),      // .seg button.active
    SegActiveText = Color(0xFFFFFFFF),    // .seg button.active 白字
    TextPrimary = Color(0xFFE6ECF6),      // --tx
    TextSecondary = Color(0xFF8FA0B8),    // --tx-2
    TextOnPrimary = Color(0xFFFFFFFF),    // .btn-primary 白字
    TextHint = Color(0xFF4C5C74),         // input::placeholder
    TextDisabled = Color(0xFF5F7089),     // --tx-3
    SurfaceVariant = Color(0xFF0D1422),   // .sidebar
    PanelHeader = Color(0xFF0D1524),      // .tabs / .con-head / --field
    Border = Color(0xFF263450),           // .pfield input 描边
    Divider = Color(0xFF1B2738),          // --line 合成（rgba(148,163,184,.08) 于 panel）
    AccentGreen = Color(0xFF4ADE80),      // .a-icon.green
    AccentRed = Color(0xFFF87171),        // .a-icon.red
    AccentBlue = Color(0xFF60A5FA),       // .a-icon.blue
    AccentOrange = Color(0xFFFB923C),     // .a-icon.orange
    AccentGrey = Color(0xFF9FB0C8),       // .a-icon.gray
    AccentSky = Color(0xFF38BDF8),        // .a-icon.sky（--cyan）
    AccentRose = Color(0xFFFB7185),       // .a-icon.rose
    TooltipBackground = Color(0xFF141E32), // .toast 底（rgba(20,30,50,.92)）
    TooltipText = Color(0xFFDBE6F5),      // .toast 文字
    FindHighlight = Color(0xFF1D3358),    // 蓝调暗底推导（seg active 系）
    FindCurrent = Color(0xFF2A4A7F),      // 更强一级命中
    LogDebug = Color(0xFF5C6C84),         // .lv-dim
    LogInfo = Color(0xFFC2CEDE),          // .lv-info
    LogWarn = Color(0xFFFBBF24),          // .lv-warn
    LogError = Color(0xFFF87171),         // .lv-err
    LogStamp = Color(0xFF7DB4FA)          // 蓝系强调字（.q-pct/.f-act 同源）
)

/** 当前组合中的扩展调色板，由 [EasyAdbTheme] 按明暗提供 */
val LocalEasyAdbColors = staticCompositionLocalOf { LightEasyAdbColors }

/** 便捷访问器：Composable 内直接 easyAdbColors.Hover / easyAdbColors.TextPrimary */
val easyAdbColors: EasyAdbColors
    @Composable get() = LocalEasyAdbColors.current

/**
 * 日志级别 → 主题化颜色（对齐 HTML 控制台 .lv-* 行级配色：dim/info/warn/err，
 * Stamp 对应命令回显的蓝系强调字）。
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
