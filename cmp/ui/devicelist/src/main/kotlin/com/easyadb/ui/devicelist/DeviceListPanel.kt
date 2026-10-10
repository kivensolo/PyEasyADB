@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.devicelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.DropdownMenu
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import com.easyadb.core.device.DeviceState
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.AppImages
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground

/**
 * 一次完整的「设备列表」面板交互回调集合。
 */
@Immutable
data class DeviceListCallbacks(
    val onDeviceClick: (DeviceRecord) -> Unit = {},
    val onDeviceDoubleClick: (DeviceRecord) -> Unit = {},
    val onDeviceAliasEdit: (DeviceRecord) -> Unit = {},
    val onDeviceDisconnect: (DeviceRecord) -> Unit = {},
    val onDeviceRemove: (DeviceRecord) -> Unit = {},
    /** 头部 [+] 添加按钮（对齐设计图设备列表头的「添加」） */
    val onAddDevice: () -> Unit = {},
    /** 命令单击（占位记录日志等轻量行为） */
    val onCommandClick: (com.easyadb.core.config.CmdItem) -> Unit = {},
    /** 命令双击执行（ActionCmdParams 组装 + adbExecutor，快捷 chips 与更多命令弹窗共用） */
    val onCommandDoubleClick: (com.easyadb.core.config.CmdItem) -> Unit = {}
)

/**
 * 侧栏设备面板（P12 布局大改——设计图卡片化）。
 *
 * 结构：
 * ```
 * Column {
 *   Header("设备列表" + [+]添加 + [«]折叠)
 *   LazyColumn {
 *     DeviceCard ...   // 设备卡片：图标 + 别名 + IP·Android版本 + 状态点
 *   }
 * }
 * ```
 *
 * 列表由 DevicesWatcher 轮询自动更新，无手动刷新入口；
 * 折叠由宿主持有状态（折叠后头栏出现展开按钮）。
 */
@Composable
fun DeviceListPanel(
    dbDevices: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>,
    selectedDeviceIp: String?,
    callbacks: DeviceListCallbacks,
    onCollapse: () -> Unit,
    deviceVersions: Map<String, String> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val deviceCards = remember(dbDevices, onlineDevices) { buildDeviceCards(dbDevices, onlineDevices) }
    val deviceStateIcons = rememberDeviceStateIcons()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(easyAdbColors.Surface)
    ) {
        // ── 头部：品牌蓝竖条 + 标题 + 弹性 + [+]添加 + [«]折叠 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(easyAdbColors.Surface)
                .padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(12.dp)
                    .clip(easyAdbTokens.shapeSm)
                    .background(easyAdbColors.Primary)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "设备列表",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextPrimary
            )
            Spacer(Modifier.weight(1f))
            // 「+ 添加」文字胶囊（对齐 HTML 侧栏 pill-btn：浅底描边 + 加号图标）
            AddDevicePillButton(onClick = callbacks.onAddDevice)
            Spacer(Modifier.width(2.dp))
            HeaderIconButton(desc = "折叠侧栏", onClick = onCollapse) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = easyAdbColors.TextSecondary,
                    modifier = Modifier.size(15.dp)
                )
            }
        }

        // ── 设备卡片列表 + 滚动条 ──
        val listState = rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                if (deviceCards.isEmpty()) {
                    item {
                        EmptyState(
                            icon = AppIcons.Android,
                            title = "暂无设备",
                            subtitle = "点击「添加」按钮添加第一台设备",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                } else {
                    items(deviceCards, key = { it.id }) { card ->
                        DeviceCard(
                            card = card,
                            androidVersion = deviceVersions[card.record.ip],
                            isSelected = selectedDeviceIp == card.record.ip,
                            stateIcons = deviceStateIcons,
                            onClick = { callbacks.onDeviceClick(card.record) },
                            onDoubleClick = { callbacks.onDeviceDoubleClick(card.record) },
                            onAliasEdit = { callbacks.onDeviceAliasEdit(card.record) },
                            onDisconnect = { callbacks.onDeviceDisconnect(card.record) },
                            onRemove = { callbacks.onDeviceRemove(card.record) }
                        )
                    }
                }
            }
            VerticalScrollbar(
                adapter = rememberScrollbarAdapter(listState),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(5.dp)
            )
        }
    }
}

/**
 * 「+ 添加」文字胶囊按钮（对齐 HTML 侧栏 pill-btn）：浅底描边 + 加号图标 +
 * 文字，hover 加深底色；「新建设备连接」对话框由宿主 callbacks.onAddDevice 唤起。
 */
@Composable
private fun AddDevicePillButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = easyAdbTokens.shapeMd,
        color = if (isHovered) easyAdbColors.Selected else easyAdbColors.Hover,
        border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = easyAdbColors.TextPrimary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "添加",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextPrimary
            )
        }
    }
}

/** 头部小图标按钮（[«] 折叠）：26dp 圆角 chip + 悬浮淡底。 */
@Composable
private fun HeaderIconButton(desc: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(easyAdbTokens.shapeMd)
            .hoverBackground(shape = easyAdbTokens.shapeMd)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

/**
 * 单个设备卡片（对齐设计图）：设备图标 + 别名 + IP·Android版本 + 状态点。
 * 选中 = 品牌描边 + Selected 底；支持双击连接与右键菜单（备注设置/断开连接/删除设备）。
 */
@Composable
private fun DeviceCard(
    card: DeviceCardData,
    androidVersion: String?,
    isSelected: Boolean,
    stateIcons: DeviceStateIcons,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit,
    onAliasEdit: () -> Unit,
    onDisconnect: () -> Unit,
    onRemove: () -> Unit
) {
    val hoverSource = remember { MutableInteractionSource() }
    val isHovered by hoverSource.collectIsHoveredAsState()
    val density = LocalDensity.current

    // 右键菜单（对齐 Python MainWindow.on_ip_menu_show：
    // 在线 → 断开可用、删除禁用；离线 → 删除可用、断开禁用）
    var showContextMenu by remember { mutableStateOf(false) }
    var menuOffset by remember { mutableStateOf(DpOffset.Zero) }

    // 双击检测：clickable 零延迟单击 + 300ms 内二次点击触发双击连接
    var lastClickTimeMs by remember(card.id) { mutableStateOf(0L) }

    // 右键菜单图标在 Composable 上下文取（painterResource 不可在 remember lambda 内调用）；
    // painterResource 内部有缓存，直接构建列表即可，无需 remember
    val contextMenuItems = listOf(
        ContextMenuItem(
            id = "alias_edit",
            name = "| 备注设置",
            enabled = true,
            icon = AppImages.edit(),
            onClick = onAliasEdit
        ),
        ContextMenuItem(
            id = "disconnect",
            name = "| 断开连接",
            enabled = card.isOnline,
            icon = AppImages.deviceDisconnected(),
            onClick = onDisconnect
        ),
        ContextMenuItem(
            id = "remove_device",
            name = "| 删除设备",
            enabled = !card.isOnline,
            icon = AppImages.warning(),
            onClick = onRemove
        )
    )

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(easyAdbTokens.shapeLg)
                .background(
                    when {
                        isSelected -> easyAdbColors.Selected
                        isHovered -> easyAdbColors.Hover
                        else -> easyAdbColors.Surface
                    }
                )
                .then(
                    if (isSelected) Modifier.border(
                        easyAdbTokens.strokeWidth, easyAdbColors.Primary, easyAdbTokens.shapeLg
                    ) else Modifier
                )
                .clickable(
                    interactionSource = hoverSource,
                    indication = null
                ) {
                    val now = System.currentTimeMillis()
                    onClick()
                    if (now - lastClickTimeMs < 300L) onDoubleClick()
                    lastClickTimeMs = now
                }
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        val pos = event.changes.first().position
                        menuOffset = with(density) {
                            DpOffset(pos.x.toDp(), pos.y.toDp())
                        }
                        showContextMenu = true
                    }
                }
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 设备图标
            Icon(
                imageVector = AppIcons.Android,
                contentDescription = null,
                tint = if (card.isOnline) easyAdbColors.Primary else easyAdbColors.TextDisabled,
                modifier = Modifier.size(30.dp)
            )
            Spacer(Modifier.width(8.dp))

            // 别名 + IP·版本
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.record.alias.ifBlank {
                        if (card.record.port == "0") card.record.ip
                        else "${card.record.ip}:${card.record.port}"
                    },
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = easyAdbColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(1.dp))
                Text(
                    text = buildString {
                        append(if (card.record.port == "0") card.record.ip else "${card.record.ip}:${card.record.port}")
                        if (!androidVersion.isNullOrBlank()) append(" · Android ").append(androidVersion)
                    },
                    fontSize = 11.sp,
                    color = easyAdbColors.TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // 状态点 + 文案
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = card.statePainter(stateIcons),
                    contentDescription = "设备状态 ${card.state?.value ?: "unknown"}",
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = card.stateLabel,
                    fontSize = 10.sp,
                    color = card.stateColor(easyAdbColors)
                )
            }
        }

        // 右键菜单（对齐 Python QMenu：图标 + 文案，禁用态灰化）
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = menuOffset
        ) {
            contextMenuItems.forEach { item ->
                Box(
                    modifier = Modifier
                        .widthIn(min = 140.dp)
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .height(28.dp)
                        .clip(easyAdbTokens.shapeMd)
                        .background(
                            if (item.enabled) Color.Transparent
                            else MaterialTheme.colors.onSurface.copy(alpha = 0.04f)
                        )
                        .then(if (item.enabled) Modifier.hoverBackground() else Modifier)
                        .clickable(
                            interactionSource = null,
                            indication = null,
                            enabled = item.enabled
                        ) {
                            showContextMenu = false
                            item.onClick()
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
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
                            color = if (item.enabled) easyAdbColors.TextPrimary
                            else easyAdbColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 设备卡片数据：数据库记录 + 实时状态合并结果。
 */
private data class DeviceCardData(
    val record: DeviceRecord,
    val online: Boolean,
    val state: DeviceState?
) {
    val id: String get() = "device.${record.ip}:${record.port}"

    /** 设备是否已在线（在线 = state 为 device/offline，可执行 ADB 命令）。 */
    val isOnline: Boolean get() = online && (state == DeviceState.DEVICE || state == DeviceState.OFFLINE)

    val stateLabel: String
        get() = when (state) {
            DeviceState.DEVICE -> "在线"
            DeviceState.OFFLINE -> "离线"
            DeviceState.UNAUTHORIZED -> "未授权"
            else -> "未连接"
        }

    /** 状态色（返回映射函数而非 @Composable getter，调用处在 Composable 上下文执行） */
    fun stateColor(colors: com.easyadb.ui.designsystem.EasyAdbColors): Color = when (state) {
        DeviceState.DEVICE -> colors.StatusOnline
        DeviceState.OFFLINE -> colors.StatusOffline
        DeviceState.UNAUTHORIZED -> colors.StatusUnauthorized
        else -> colors.TextHint
    }

    fun statePainter(icons: DeviceStateIcons): Painter = when (state) {
        DeviceState.DEVICE -> icons.connected
        DeviceState.OFFLINE -> icons.offline
        DeviceState.UNAUTHORIZED -> icons.disconnected
        else -> icons.disconnected
    }
}

/**
 * 从 [records]（数据库）与 [onlineDevices]（DeviceWatcher）合并出设备卡片数据。
 * 数据库中保存的设备始终展示（含离线），Watcher 推送的 state 驱动状态显示。
 */
private fun buildDeviceCards(
    records: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>
): List<DeviceCardData> {
    val stateByName = onlineDevices.associateBy { it.name }
    return records.sortedWith(compareBy({ it.ip }, { it.port })).map { record ->
        val portPart = if (record.port == "0") "" else ":${record.port}"
        val matched = stateByName[record.ip + portPart]
        DeviceCardData(
            record = record,
            online = matched != null,
            state = matched?.state
        )
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
 * 避免每张卡片重复加载 PNG。
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
