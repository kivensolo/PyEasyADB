package com.easyadb.ui.devicelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.DropdownMenu
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.device.DeviceState
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
 *
 * 右键菜单：仅设备节点提供 [contextMenuItems]（对应 Python MainWindow 中
 * `tree_view.customContextMenuRequested` + QMenu 弹出的备注设置/断开连接/删除设备）。
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)
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
    deviceStateIcons: DeviceStateIcons? = null,
    /** 右键菜单项；非 null 时启用 secondary tap（鼠标右键）响应。 */
    contextMenuItems: List<ContextMenuItem>? = null
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()

    var showContextMenu by remember { mutableStateOf(false) }
    // 记录右键按下时的鼠标位置（相对 TreeItem 行的左上角），
    // 作为 DropdownMenu 的锚点偏移，让菜单从鼠标点弹出（对齐 Python
    // MainWindow.on_ip_menu_show 中的 self.tree_view.contextMenu.move(QCursor.pos())）。
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current

    val backgroundColor = when {
        isSelected -> MaterialTheme.colors.primary.copy(alpha = 0.18f)
        isHovered -> MaterialTheme.colors.onSurface.copy(alpha = 0.06f)
        else -> Color.Transparent
    }

    // 行级手势：分组节点保留双击；叶子节点零延迟单击；
    // 二者均可响应 secondary tap（鼠标右键），通过 Modifier.onPointerEvent 捕获按下事件
    // 并记录位置，让 DropdownMenu 以该位置为偏移弹出。
    val secondaryTapModifier = if (contextMenuItems != null) {
        Modifier.onPointerEvent(PointerEventType.Press) { event ->
            if (event.buttons.isSecondaryPressed) {
                val pos = event.changes.first().position
                menuOffset = with(density) {
                    DpOffset(pos.x.toDp(), pos.y.toDp())
                }
                showContextMenu = true
            }
        }
    } else Modifier

    // 双击检测：用 clickable 保证单击零延迟，通过时间戳判断双击。
    // supportsDoubleClick=true 时，300ms 内再次点击额外触发 onDoubleClick。
    // supportsDoubleClick=false 时，仅响应单击。
    var lastClickTimeMs by remember(node.id) { mutableStateOf(0L) }
    val doubleTapTimeoutMs = 300L
    val rowModifier = modifier
        .fillMaxWidth()
        .background(backgroundColor)
        .clickable(
            interactionSource = hoverSource,
            indication = null
        ) {
            val now = System.currentTimeMillis()
            onClick()
            if (supportsDoubleClick && now - lastClickTimeMs < doubleTapTimeoutMs) {
                onDoubleClick()
            }
            lastClickTimeMs = now
        }
        .then(secondaryTapModifier)
        .padding(horizontal = 4.dp, vertical = 2.dp)

    Box(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = rowModifier,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 缩进
            Spacer(Modifier.width((node.depth * 12).dp))

            // 展开/折叠图标：仅命令树的分组/子分组节点显示 +/-（对齐 Python QTreeView）。
            // 设备根 / 命令根 两层不显示切换符号，仍可通过双击行折叠。
            val showExpandToggle = hasChildren && when (node) {
                is TreeNode.DeviceGroupRootNode -> false
                is TreeNode.CommandGroupRootNode -> false
                else -> true
            }
            if (showExpandToggle) {
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .pointerInput(node.id) {
                            detectTapGestures(onTap = { onToggleExpand() })
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpanded) "-" else "+",
                        fontSize = 14.sp,
                        color = EasyAdbColors.TextSecondary,
                        fontWeight = FontWeight.Bold
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
                Image(
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

        // 右键菜单（对齐 Python MainWindow.on_ip_menu_show + QMenu）
        if (contextMenuItems != null) {
            DropdownMenu(
                expanded = showContextMenu,
                onDismissRequest = { showContextMenu = false },
                offset = menuOffset
            ) {
                Column {
                    contextMenuItems.forEach { item ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(
                                    if (item.enabled) Color.Transparent
                                    else MaterialTheme.colors.onSurface.copy(alpha = 0.04f)
                                )
                                .then(
                                    if (item.enabled) Modifier.padding(horizontal = 16.dp)
                                    else Modifier.padding(horizontal = 16.dp)
                                )
                                .clickable(enabled = item.enabled) {
                                    showContextMenu = false
                                    item.onClick()
                                },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (item.icon != null) {
                                    Image(
                                        painter = item.icon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier
                                            .size(21.dp)
                                            .then(if (item.enabled) Modifier else Modifier.alpha(0.4f)),
                                        // 禁用时把彩色图标转为灰度（对齐 Qt QIcon 的 Disabled mode）
                                        colorFilter = if (item.enabled) null
                                        else ColorFilter.colorMatrix(
                                            ColorMatrix(
                                                floatArrayOf(
                                                    0.299f, 0.587f, 0.114f, 0f, 0f,
                                                    0.299f, 0.587f, 0.114f, 0f, 0f,
                                                    0.299f, 0.587f, 0.114f, 0f, 0f,
                                                    0f, 0f, 0f, 1f, 0f
                                                )
                                            )
                                        )
                                    )
                                    Spacer(Modifier.width(6.dp))
                                }
                                Text(
                                    text = item.name,
                                    fontSize = 12.sp,
                                    color = if (item.enabled) EasyAdbColors.TextPrimary
                                    else EasyAdbColors.TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * 通用右键菜单项。对应 Python QMenu 的一个 QAction。
 *
 * @param icon 图标 painter（对齐 Python addAction(icon, text) 的第一个参数），
 *             null 时不显示图标。
 */
data class ContextMenuItem(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val icon: Painter? = null,
    val onClick: () -> Unit
)

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
