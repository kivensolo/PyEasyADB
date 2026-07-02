package com.easyadb.ui.home.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbColors
import java.awt.Cursor

data class BottomTab(
    val id: String,
    val title: String,
    val content: @Composable () -> Unit
)

@Composable
fun BottomTabHost(
    modifier: Modifier = Modifier,
    tabs: List<BottomTab>,
    defaultHeight: Dp = 200.dp,
    minHeight: Dp = 100.dp,
    maxHeight: Dp = 600.dp
) {
    var selectedTab by remember { mutableStateOf(tabs.firstOrNull()?.id) }
    var isExpanded by remember { mutableStateOf(true) }
    var currentHeight by remember { mutableStateOf(defaultHeight) }
    val density = LocalDensity.current

    Column(modifier = modifier) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically(expandFrom = Alignment.Bottom, animationSpec = tween(200)),
            exit = shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = tween(200))
        ) {
            Column {
                HorizontalDragHandle(
                    onDrag = { deltaPixels ->
                        val deltaDp = with(density) { (-deltaPixels).toDp() }
                        currentHeight = (currentHeight + deltaDp).coerceIn(minHeight, maxHeight)
                    }
                )
                Box(
                    modifier = Modifier.fillMaxWidth().height(currentHeight)
                        .background(MaterialTheme.colors.surface)
                ) {
                    tabs.find { it.id == selectedTab }?.content?.invoke()
                }
            }
        }

        BottomTabHeader(
            tabs = tabs, selectedTab = selectedTab, isExpanded = isExpanded,
            onTabClick = { tabId ->
                if (selectedTab == tabId && isExpanded) isExpanded = false
                else { selectedTab = tabId; isExpanded = true }
            },
            onToggleClick = { isExpanded = !isExpanded }
        )
    }
}

@Composable
private fun HorizontalDragHandle(
    onDrag: (Float) -> Unit,
    thickness: Dp = 4.dp
) {
    var isDragging by remember { mutableStateOf(false) }
    val backgroundColor = if (isDragging) MaterialTheme.colors.primary
        else MaterialTheme.colors.onSurface.copy(alpha = 0.08f)

    Box(
        modifier = Modifier.fillMaxWidth().height(thickness)
            .background(backgroundColor)
            .pointerHoverIcon(PointerIcon(Cursor.getPredefinedCursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { isDragging = true },
                    onDragEnd = { isDragging = false },
                    onDragCancel = { isDragging = false }
                ) { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            }
    )
}

@Composable
private fun BottomTabHeader(
    tabs: List<BottomTab>, selectedTab: String?, isExpanded: Boolean,
    onTabClick: (String) -> Unit, onToggleClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp)
            .background(EasyAdbColors.PanelHeader),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isSelected = tab.id == selectedTab
            Box(
                modifier = Modifier.height(32.dp).widthIn(min = 80.dp)
                    .background(if (isSelected) MaterialTheme.colors.surface else Color.Transparent)
                    .clickable { onTabClick(tab.id) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tab.title, fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = if (isSelected) MaterialTheme.colors.primary else EasyAdbColors.TextSecondary
                )
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = if (isExpanded) AppIcons.ArrowDown else AppIcons.ArrowUp,
            contentDescription = if (isExpanded) "折叠" else "展开",
            modifier = Modifier.padding(horizontal = 8.dp).clickable { onToggleClick() },
            tint = EasyAdbColors.TextSecondary
        )
    }
}