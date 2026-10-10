package com.easyadb.ui.home.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.LogcatStream
import com.easyadb.ui.console.ConsolePanel
import com.easyadb.ui.console.LogEntry
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground
import com.easyadb.ui.logcat.LogcatPanel
import kotlinx.coroutines.flow.MutableStateFlow
import java.awt.Cursor

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/9
 * description：右栏底部内嵌控制台区（P12 布局大改——替代原窗口底部折叠区）。
 *              头部行 = 「控制台/实时日志」双 Tab chips + 右侧收缩按钮（对齐左栏
 *              设备列表折叠交互，点击箭头整区隐藏，收缩后头部行变展开按钮条）。
 *              内容区按 Tab 切换 [ConsolePanel] 或 [LogcatPanel]。
 *              顶部拖拽条可调整区域高度（对齐原 BottomTabHost 拖拽语言）。
 *
 * @param collapsed 是否收缩（状态由宿主持有，收缩后仅显示展开按钮条）
 * @param onToggleCollapse 收缩/展开切换回调（宿主只改状态）
 */
@Composable
fun EmbeddedConsoleHost(
    consoleLogFlow: MutableStateFlow<List<LogEntry>>,
    logcatStream: LogcatStream,
    deviceIp: String?,
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    collapsed: Boolean,
    onToggleCollapse: () -> Unit,
    modifier: Modifier = Modifier,
    defaultHeight: Dp = 260.dp
) {
    var currentHeight by remember { mutableStateOf(defaultHeight) }
    val density = LocalDensity.current

    if (collapsed) {
        // 收缩态：仅显示一条展开按钮条（对齐左栏折叠后头栏出现 [»] 的交互语言）
        Surface(color = easyAdbColors.Surface, elevation = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(30.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ExpandChip(icon = AppIcons.ArrowUp, title = "展开控制台", onClick = onToggleCollapse)
            }
        }
        return
    }

    Column(modifier = modifier) {
        // 顶部拖拽条（向下拖增高）
        DragHandle(
            onDrag = { deltaPixels ->
                val deltaDp = with(density) { (-deltaPixels).toDp() }
                currentHeight = (currentHeight + deltaDp).coerceIn(120.dp, 480.dp)
            }
        )

        // 头部行：双 Tab chips + 弹性 + 收缩按钮
        Surface(color = easyAdbColors.Surface, elevation = 0.dp) {
            Row(
                modifier = Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HostTabChip(title = "控制台", selected = selectedTab == 0, onClick = { onSelectTab(0) })
                HostTabChip(title = "实时日志", selected = selectedTab == 1, onClick = { onSelectTab(1) })
                Spacer(Modifier.weight(1f))
                HostTabChip(
                    title = "",
                    selected = false,
                    onClick = onToggleCollapse,
                    leadingIcon = AppIcons.ArrowDown,
                    contentDesc = "收缩控制台"
                )
            }
        }

        // 内容区
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(currentHeight)
                .background(easyAdbColors.Surface)
        ) {
            when (selectedTab) {
                1 -> LogcatPanel(
                    deviceIp = deviceIp,
                    logcatStream = logcatStream,
                    modifier = Modifier.fillMaxWidth()
                )
                else -> ConsolePanel(
                    logFlow = consoleLogFlow,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * 内嵌区 Tab chip（对齐原 BottomTabHost 圆角 chip 语言：选中 Selected 底 + Primary 文字）。
 * [leadingIcon] 非空时渲染为图标 chip（收缩/展开按钮复用）。
 */
@Composable
private fun HostTabChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    leadingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    contentDesc: String = title
) {
    Box(
        modifier = Modifier
            .padding(end = 4.dp, top = 3.dp, bottom = 3.dp)
            .height(28.dp).widthIn(min = if (leadingIcon != null) 28.dp else 72.dp)
            .clip(easyAdbTokens.shapeMd)
            .background(if (selected) easyAdbColors.Selected else Color.Transparent)
            .hoverBackground(shape = easyAdbTokens.shapeMd)
            .clickable(interactionSource = null, indication = null) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = contentDesc,
                tint = easyAdbColors.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        } else {
            Text(
                text = title, fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) easyAdbColors.Primary else easyAdbColors.TextSecondary
            )
        }
    }
}

/**
 * 收缩态展开按钮条：图标 + 文字，hover 淡底。
 * 箭头向上示意「向上展开」内容区（收缩态在窗口底部）。
 */
@Composable
private fun ExpandChip(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(start = 6.dp, top = 3.dp, bottom = 3.dp)
            .height(24.dp)
            .clip(easyAdbTokens.shapeMd)
            .hoverBackground(shape = easyAdbTokens.shapeMd)
            .clickable(interactionSource = null, indication = null) { onClick() }
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = easyAdbColors.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.size(4.dp))
        Text(
            text = title,
            fontSize = 11.sp,
            color = easyAdbColors.TextSecondary
        )
    }
}

/** 高度拖拽条（复用原 BottomTabHost.HorizontalDragHandle 实现）。 */
@Composable
private fun DragHandle(onDrag: (Float) -> Unit, thickness: Dp = 4.dp) {
    var isDragging by remember { mutableStateOf(false) }
    val backgroundColor = if (isDragging) easyAdbColors.Primary else easyAdbColors.Divider

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
