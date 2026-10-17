package com.easyadb.ui.designsystem

import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 设计 token（P12）：圆角 / 间距 / 描边 / elevation 成体系。
 *
 * 与颜色无关，明暗共用一套数值——视觉分层由 [EasyAdbColors] 表达，形状与节奏由这里表达。
 * 数值收敛自现状盘点：圆角现存 4/5/6/8/12 五档收敛为 4/6/8/12 四档（5→6）；
 * 间距取 4dp 栅格五档；elevation 按浮起高度三档；描边统一 1dp。
 *
 * 组件翻新（P12）一律消费 token，不再出现字面 dp 圆角/间距。
 */
@Immutable
data class EasyAdbTokens(
    // ── 圆角体系 ──
    /** 4dp：控件内小元素（GroupBox、分段控件内部） */
    val radiusSm: Dp,
    /** 6dp：按钮 / 输入框 / 列表行 chip（现使用频率最高的一档） */
    val radiusMd: Dp,
    /** 8dp：卡片 / 面板 / 下拉容器 */
    val radiusLg: Dp,
    /** 12dp：弹窗 / 浮层窗口 */
    val radiusXl: Dp,

    // ── 预烘焙 Shape（调用点直接 easyAdbTokens.shapeMd，避免每处再包 RoundedCornerShape） ──
    // 用 CornerBasedShape：可直接喂给 MaterialTheme.shapes（其 small/medium/large 形参即此类型）
    val shapeSm: CornerBasedShape,
    val shapeMd: CornerBasedShape,
    val shapeLg: CornerBasedShape,
    val shapeXl: CornerBasedShape,

    // ── 间距栅格（4dp 基准） ──
    /** 4dp：图标与文字、行内元素间隙 */
    val spaceXs: Dp,
    /** 8dp：组内元素、紧凑列表行距 */
    val spaceSm: Dp,
    /** 12dp：卡片内边距、分区间隙 */
    val spaceMd: Dp,
    /** 16dp：区块内边距 */
    val spaceLg: Dp,
    /** 24dp：区块间距、页面级留白 */
    val spaceXl: Dp,

    // ── elevation 层级（克制阴影：大多数平面 0，浮起才给） ──
    /** 2dp：内嵌卡片 / FindBar 等轻浮起 */
    val elevationLow: Dp,
    /** 4dp：Tooltip / 小型浮层 */
    val elevationMid: Dp,
    /** 8dp：菜单 / 弹窗级浮层 */
    val elevationHigh: Dp,

    // ── 描边 ──
    /** 1dp：所有边框/分割（粗细统一，颜色走调色板 Border/Divider） */
    val strokeWidth: Dp
)

/** 默认 token 值（明暗共用；调整全局节奏只改这里） */
val EasyAdbTokensDefault = EasyAdbTokens(
    radiusSm = 4.dp,
    radiusMd = 6.dp,
    radiusLg = 8.dp,
    radiusXl = 12.dp,
    shapeSm = RoundedCornerShape(4.dp),
    shapeMd = RoundedCornerShape(6.dp),
    shapeLg = RoundedCornerShape(8.dp),
    shapeXl = RoundedCornerShape(12.dp),
    spaceXs = 4.dp,
    spaceSm = 8.dp,
    spaceMd = 12.dp,
    spaceLg = 16.dp,
    spaceXl = 24.dp,
    elevationLow = 2.dp,
    elevationMid = 4.dp,
    elevationHigh = 8.dp,
    strokeWidth = 1.dp
)

/** 当前组合中的设计 token，由 [EasyAdbTheme] 提供 */
val LocalEasyAdbTokens = staticCompositionLocalOf { EasyAdbTokensDefault }

/** 便捷访问器：Composable 内直接 easyAdbTokens.shapeLg / easyAdbTokens.spaceSm */
val easyAdbTokens: EasyAdbTokens
    @Composable get() = LocalEasyAdbTokens.current
