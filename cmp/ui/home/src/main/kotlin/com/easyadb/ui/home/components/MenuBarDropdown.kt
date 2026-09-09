package com.easyadb.ui.home.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties

/**
 * 紧贴锚点底边展开的下拉弹层，供顶部菜单栏使用。
 *
 * 不复用 material 的 DropdownMenu：其桌面端 DropdownMenuPositionProvider 会把弹层
 * 推到距窗口顶部至少 48dp（MenuVerticalMargin 按主窗口而非屏幕取值），菜单栏锚点
 * 位于窗口顶部 28dp 内时被 clamp，弹层与标题之间凭空多出约 20dp 的间距（传负 offset
 * 也无法抵消，因为 clamp 取的是 max(anchor.bottom + offset, 48dp)）；此外其内容列
 * 自带 8dp 上下内边距，进一步拉开与标题的视觉距离。
 */
private class MenuBarDropdownPositionProvider : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize
    ): IntOffset {
        val x = anchorBounds.left
            .coerceAtLeast(0)
            .coerceAtMost((windowSize.width - popupContentSize.width).coerceAtLeast(0))
        // 默认在锚点下方展开；窗口剩余高度不足时翻转到上方
        val y = if (anchorBounds.bottom + popupContentSize.height <= windowSize.height) {
            anchorBounds.bottom
        } else {
            (anchorBounds.top - popupContentSize.height).coerceAtLeast(0)
        }
        return IntOffset(x, y)
    }
}

/**
 * 菜单栏下拉菜单容器。
 *
 * 展开时渲染为 focusable 弹层：点击外部自动回调 [onDismissRequest]，Esc 键关闭；
 * 内容列以最宽项为准对齐（IntrinsicSize.Max），保证快捷键列右对齐于一列。
 */
@Composable
fun MenuBarDropdown(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    if (!expanded) return

    Popup(
        popupPositionProvider = MenuBarDropdownPositionProvider(),
        onDismissRequest = onDismissRequest,
        properties = PopupProperties(focusable = true)
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .width(IntrinsicSize.Max)
                    .onPreviewKeyEvent { event ->
                        if (event.key == Key.Escape && event.type == KeyEventType.KeyDown) {
                            onDismissRequest()
                            true
                        } else {
                            false
                        }
                    }
            ) {
                content()
            }
        }
    }
}
