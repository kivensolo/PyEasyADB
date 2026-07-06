package com.easyadb.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.device.DeviceState
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.AppImages
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 单行树节点视图。
 *
 * 性能要点：
 * 1. **叶子节点（设备 / 命令项）走 `Modifier.clickable`**——零延迟，避免
 *    `detectTapGestures(onTap, onDoubleTap)` 内部等待 doubleTapTimeout
 *    (~200ms) 才能区分单击导致的明显卡顿。
 * 2. **只有分组节点（设备根 / 命令根 / 分组 / 子分组）保留双击折叠**——
 *    这些节点数量很少，双击延迟可接受；用 `pointerInput` 识别双击。
 * 3. **`painterResource` 由上层 [DeviceListPanel] 通过 [DeviceStateIcons] 缓存
 *    注入**，避免每个节点重复加载 PNG。
 */
@Composable
fun TreeItem(
    node: TreeNode,
    isSelected: Boolean,
    isExpanded: Boolean,
    hasChildren: Boolean,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onToggleExpand: () -> Unit,
    modifier: Modifier = Modifier,
    /** 是否支持双击。叶子节点传 false（零延迟单击），分组节点传 true。 */
    supportsDoubleClick: Boolean = hasChildren,
    /** 设备状态 painter 缓存（仅设备节点使用）。 */
    deviceStateIcons: DeviceStateIcons? = null
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    val backgroundColor = when {
        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.18f)
        isHovered -> MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    val rowModifier = if (supportsDoubleClick) {
        // 分组节点：pointerInput 同时识别单击（切换展开）与双击（同样切换展开）
        modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .pointerInput(node.id) {
                detectTapGestures(
                    onTap = { onClick() },
                    onDoubleTap = { onDoubleClick() }
                )
            }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    } else {
        // 叶子节点：纯 clickable，无延迟
        modifier
            .fillMaxWidth()
            .background(backgroundColor)
            .clickable(
                interactionSource = hoverSource,
                indication = null
            ) { onClick() }
            .padding(horizontal = 4.dp, vertical = 2.dp)
    }

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 缩进
        Spacer(Modifier.width((node.depth * 12).dp))

        // 展开/折叠图标
        if (hasChildren) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .pointerInput(node.id) {
                        detectTapGestures(onTap = { onToggleExpand() })
                    },
                contentAlignment = Alignment.Center
            ) {
                val arrow = if (isExpanded) AppIcons.ArrowDown else AppIcons.ArrowRight
                Icon(
                    imageVector = arrow,
                    contentDescription = if (isExpanded) "折叠" else "展开",
                    tint = EasyAdbColors.TextSecondary,
                    modifier = Modifier.size(14.dp)
                )
            }
        } else {
            Spacer(Modifier.width(20.dp))
        }

        // 设备状态图标（painter 由上层注入，避免每行 painterResource）
        if (node is TreeNode.DeviceNode && deviceStateIcons != null) {
            val statePainter = when (node.state) {
                DeviceState.DEVICE -> deviceStateIcons.connected
                DeviceState.OFFLINE -> deviceStateIcons.offline
                DeviceState.UNAUTHORIZED -> deviceStateIcons.disconnected
                else -> deviceStateIcons.disconnected
            }
            androidx.compose.foundation.Image(
                painter = statePainter,
                contentDescription = "设备状态 ${node.state?.value ?: "unknown"}",
                modifier = Modifier
                    .padding(end = 6.dp)
                    .size(14.dp)
            )
        }

        // 文本
        val displayText = when (node) {
            is TreeNode.DeviceGroupRootNode -> node.displayName
            is TreeNode.CommandGroupRootNode -> node.displayName
            is TreeNode.DeviceNode -> node.displayText
            is TreeNode.CommandGroupNode -> node.group.name
            is TreeNode.CommandSubGroupNode -> node.subGroup.name
            is TreeNode.CommandNode -> node.item.name
        }
        val fontWeight = if (node is TreeNode.DeviceGroupRootNode || node is TreeNode.CommandGroupRootNode) {
            FontWeight.Bold
        } else FontWeight.Normal

        Text(
            text = displayText,
            fontSize = 12.sp,
            color = EasyAdbColors.TextPrimary,
            fontWeight = fontWeight,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(end = 4.dp)
        )
    }
}

/**
 * 设备状态图标的 painter 缓存。
 * 在 [DeviceListPanel] 顶层通过 `remember { DeviceStateIcons(...) }` 创建一次，
 * 注入到每个设备节点的 TreeItem 中，避免每行重复加载 PNG。
 */
class DeviceStateIcons(
    val connected: Painter,
    val offline: Painter,
    val disconnected: Painter
)

/** 从 AppImages 加载并缓存到组合中的设备状态图标。 */
@Composable
fun rememberDeviceStateIcons(): DeviceStateIcons {
    val connected = AppImages.deviceConnected()
    val offline = AppImages.deviceOffline()
    val disconnected = AppImages.deviceDisconnected()
    return remember(connected, offline, disconnected) {
        DeviceStateIcons(connected, offline, disconnected)
    }
}
