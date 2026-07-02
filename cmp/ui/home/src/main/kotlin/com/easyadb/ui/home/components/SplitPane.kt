package com.easyadb.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import java.awt.Cursor

@Composable
fun SplitPane(
    modifier: Modifier = Modifier,
    initialFraction: Float = 0.25f,
    dividerThickness: Dp = 4.dp,
    minLeftWidthDp: Dp = 120.dp,
    minRightWidthDp: Dp = 120.dp,
    leftPanel: @Composable (Modifier) -> Unit,
    rightPanel: @Composable (Modifier) -> Unit
) {
    var fraction by remember { mutableStateOf(initialFraction) }
    var isDragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val dividerColor = if (isDragging) MaterialTheme.colors.primary
        else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)

    BoxWithConstraints(modifier = modifier) {
        val totalWidthPx = constraints.maxWidth.toFloat()
        if (totalWidthPx <= 0f) return@BoxWithConstraints

        val dividerPx = with(density) { dividerThickness.toPx() }
        val minLeftPx = with(density) { minLeftWidthDp.toPx() }
        val minRightPx = with(density) { minRightWidthDp.toPx() }

        val leftWidthPx = (totalWidthPx * fraction)
            .coerceIn(minLeftPx, totalWidthPx - dividerPx - minRightPx)
        val rightWidthPx = (totalWidthPx - leftWidthPx - dividerPx)
            .coerceAtLeast(minRightPx)

        Row(modifier = Modifier.fillMaxSize()) {
            leftPanel(
                Modifier.width(with(density) { leftWidthPx.toDp() }).fillMaxHeight()
            )
            Box(
                modifier = Modifier.width(dividerThickness).fillMaxHeight()
                    .background(dividerColor)
                    .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.E_RESIZE_CURSOR)))
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { isDragging = true },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false }
                        ) { change, dragAmount ->
                            change.consume()
                            if (totalWidthPx > 0f) {
                                fraction = (fraction + dragAmount.x / totalWidthPx).coerceIn(0.15f, 0.6f)
                            }
                        }
                    }
            )
            rightPanel(
                Modifier.width(with(density) { rightWidthPx.toDp() }).fillMaxHeight()
            )
        }
    }
}