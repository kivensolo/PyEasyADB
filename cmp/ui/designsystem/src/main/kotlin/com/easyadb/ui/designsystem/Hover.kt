package com.easyadb.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape

/**
 * 全局统一的鼠标悬浮底色。
 *
 * 底色默认取当前主题的 [EasyAdbColors.Hover]（浅色 = 品牌蓝 8%，深色 = 提亮蓝 16%，
 * 均随主题分发），替代各处零散的灰色 onSurface 半透明与 clickable 默认 ripple，
 * 让菜单项 / 列表行的悬浮反馈一致；显式传入 [color] 可覆盖主题默认值。
 *
 * 用法：放在 clickable 之前、padding 之前，保证底色铺满整行；配套把 clickable
 * 的 indication 置为 null，避免 ripple 灰色叠加：
 * ```
 * Box(
 *     modifier = Modifier
 *         .fillMaxWidth()
 *         .hoverBackground()
 *         .clickable(interactionSource = null, indication = null) { ... }
 *         .padding(horizontal = 12.dp)
 * )
 * ```
 */
fun Modifier.hoverBackground(
    color: Color? = null,
    shape: Shape = RectangleShape
): Modifier = composed {
    // 默认色在组合内读取，保证随主题切换（不能作为顶层函数默认参数，那不参与组合）
    val resolved = color ?: LocalEasyAdbColors.current.Hover
    val interactionSource = remember { MutableInteractionSource() }
    val hovered by interactionSource.collectIsHoveredAsState()
    this
        .hoverable(interactionSource)
        .background(if (hovered) resolved else Color.Transparent, shape)
}
