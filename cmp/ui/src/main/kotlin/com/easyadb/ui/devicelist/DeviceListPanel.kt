@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.devicelist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.Icon
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.window.Popup
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.database.DeviceRecord
import com.easyadb.core.device.DeviceInfo
import com.easyadb.core.device.DeviceState
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground
import kotlin.math.roundToInt

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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(easyAdbColors.SurfaceVariant) // 侧栏底色（HTML .sidebar：暗 #0d1422 / 浅 #f2f5fa）
    ) {
        // ── 头部：品牌蓝竖条 + 标题 + 弹性 + [+]添加 + [«]折叠 ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(easyAdbColors.SurfaceVariant)
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
                            // 内存缓存未命中（离线/刚重启）时回退 DB 持久值；
                            // 须含 "API" 字样——滤掉 Python 遗留库的占位值（如 '10086'）
                            androidVersion = deviceVersions[card.fullAddress]
                                ?: card.record.deviceInfo.takeIf { it.isNotBlank() && "API" in it },
                            isSelected = selectedDeviceIp == card.record.ip,
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
 * 单个设备卡片（对齐 HTML .dev-card）：渐变卡体 + 44dp 图标容器（.dev-ico）+
 * IP 主行（.dev-ip，mono 蓝）+ 别名/版本副行（.dev-sub）+ 状态点行（.dev-state，
 * 绿=已连接 黄=离线 红=未连接）。选中 = 品牌描边 + 渐变底 + 左缘渐变竖条
 * （.sel::before）；支持双击连接与右键菜单（备注设置/断开连接/删除设备）。
 */
@Composable
private fun DeviceCard(
    card: DeviceCardData,
    androidVersion: String?,
    isSelected: Boolean,
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
    // 在线 → 断开可用、删除禁用；离线 → 删除可用、断开禁用）。
    // null = 不显示；非空 = 触发点（卡片局部像素坐标），裸 Popup 按 TopStart+offset 定位——
    // 不用 material DropdownMenu：其 offset 是相对锚点的追加偏移且内部有项高钳制，
    // 传点击坐标会定位漂移（与文件树 ContextActionsMenu 同款 Popup 方案）
    var contextMenuPos by remember { mutableStateOf<Offset?>(null) }

    // 双击检测：clickable 零延迟单击 + 300ms 内二次点击触发双击连接
    var lastClickTimeMs by remember(card.id) { mutableStateOf(0L) }

    // 右键菜单（对齐 H5 .ctx-menu：线稿图标 + 文案，随主题染色）
    val contextMenuItems = listOf(
        ContextMenuItem(
            id = "alias_edit",
            name = "备注设置",
            enabled = true,
            iconVector = EasyAdbTileIcons.AliasEdit,
            onClick = onAliasEdit
        ),
        ContextMenuItem(
            id = "disconnect",
            name = "断开连接",
            enabled = card.isOnline,
            iconVector = EasyAdbTileIcons.Disconnect,
            onClick = onDisconnect
        ),
        ContextMenuItem(
            id = "remove_device",
            name = "删除设备",
            enabled = !card.isOnline,
            iconVector = EasyAdbTileIcons.RemoveDevice,
            danger = true,
            onClick = onRemove
        )
    )

    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp)) {
        // 选中竖条渐变色（组合期取出，绘制层使用）
        val selBarGradient = listOf(easyAdbColors.PrimaryLight, easyAdbColors.Primary)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.verticalGradient(
                        if (isSelected) listOf(easyAdbColors.DevCardSelTop, easyAdbColors.DevCardSelBottom)
                        else listOf(easyAdbColors.DevCardTop, easyAdbColors.DevCardBottom)
                    )
                )
                .border(
                    easyAdbTokens.strokeWidth,
                    when {
                        isSelected -> easyAdbColors.Primary   // HTML .sel border #2f6bff
                        isHovered -> easyAdbColors.Border     // .dev-card:hover border 提亮
                        else -> easyAdbColors.Divider
                    },
                    RoundedCornerShape(14.dp)
                )
                // 选中左缘渐变竖条（HTML .sel::before：宽 3px、上下内缩 14px）。
                // 用绘制层实现而非子项 Box：LazyColumn item 高度约束无界，
                // 子项 fillMaxHeight 不生效会导致竖条高度为 0 不可见；
                // 渐变色在组合期取出（drawBehind lambda 非组合上下文）
                .drawBehind {
                    if (isSelected) {
                        val barWidth = 3.dp.toPx()
                        val verticalInset = 14.dp.toPx()
                        drawRoundRect(
                            brush = Brush.verticalGradient(
                                colors = selBarGradient,
                                startY = verticalInset,
                                endY = size.height - verticalInset
                            ),
                            topLeft = Offset(0f, verticalInset),
                            size = Size(barWidth, size.height - verticalInset * 2),
                            cornerRadius = CornerRadius(barWidth / 2f)
                        )
                    }
                }
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
                        contextMenuPos = event.changes.first().position
                    }
                }
                .padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            // 图标容器（HTML .dev-ico：44dp 圆角容器 + 显示器线稿）
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(easyAdbColors.DevIcoBg)
                    .border(easyAdbTokens.strokeWidth, easyAdbColors.DevIcoBorder, RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = EasyAdbTileIcons.Monitor,
                    contentDescription = null,
                    tint = easyAdbColors.DevIcoIcon,
                    modifier = Modifier.size(23.dp)
                )
            }
            Spacer(Modifier.width(13.dp))

            // 地址主行 + 别名/版本副行 + 状态行（HTML .dev-info）
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = card.fullAddress,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = easyAdbColors.DevIpText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subLine = buildString {
                    if (card.record.alias.isNotBlank()) append(card.record.alias)
                    if (!androidVersion.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(androidVersion)  // 「名称(版本)，API 级别」标签自带语义，不加 Android 前缀
                    }
                }
                if (subLine.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = subLine,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = easyAdbColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(8.dp))
                // 状态点 + 文案（7dp 色点；绿=在线 黄=离线 红=未连接/未授权）
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(card.stateColor(easyAdbColors))
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = card.stateLabel,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = card.stateColor(easyAdbColors)
                    )
                }
            }
        }

        // 右键菜单（对齐 H5 .ctx-menu：Surface 描边+投影浮层、5dp 内衬、
        // 30dp 行高 7dp 圆角、hover 蓝底字色翻转、danger 红系、禁用置灰；
        // 与文件树 ContextActionsMenu 同一浮层语言）
        contextMenuPos?.let { pos ->
            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt()),
                onDismissRequest = { contextMenuPos = null },
                focusable = true
            ) {
                Surface(
                    shape = easyAdbTokens.shapeXl,
                    color = easyAdbColors.Surface,
                    border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Border),
                    elevation = easyAdbTokens.elevationHigh,
                    // 钳到内容固有宽度：Popup 内容最大约束为整窗宽度，
                    // 行 fillMaxWidth 会把 Surface 撑满全窗（与文件树 ContextActionsMenu 同款钳制）
                    modifier = Modifier.width(IntrinsicSize.Min)
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(min = 176.dp)   // H5 .ctx-menu min-width:176px
                            .padding(5.dp)
                    ) {
                        contextMenuItems.forEach { item ->
                            DeviceContextMenuRow(item) {
                                contextMenuPos = null
                                item.onClick()
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 右键菜单行圆角（H5 .ctx-item border-radius:7px，与文件树 MenuRowShape 同规格） */
private val MenuRowShape = RoundedCornerShape(7.dp)

/**
 * 设备右键菜单单行（H5 .ctx-item）：30dp 行高、hover 蓝底 + 字色/图标翻转、
 * danger 项红系（hover 红底）、禁用项置灰不可点。
 */
@Composable
private fun DeviceContextMenuRow(
    item: ContextMenuItem,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val active = hovered && item.enabled
    val c = easyAdbColors
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(MenuRowShape)
            .background(
                when {
                    active && item.danger -> c.AccentRed.copy(alpha = 0.16f)
                    active -> c.MenuHoverBg
                    else -> Color.Transparent
                }
            )
            .then(
                if (item.enabled) {
                    Modifier.clickable(
                        interactionSource = null,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (item.iconVector != null) {
                Icon(
                    imageVector = item.iconVector,
                    contentDescription = null,
                    tint = when {
                        !item.enabled -> c.TextDisabled.copy(alpha = 0.6f)
                        active -> c.MenuHoverText
                        item.danger -> c.AccentRed
                        else -> c.TextSecondary
                    },
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(9.dp))
            }
            Text(
                text = item.name,
                fontSize = 12.5.sp,
                color = when {
                    !item.enabled -> c.TextDisabled.copy(alpha = 0.7f)
                    active -> c.MenuHoverText
                    item.danger -> c.AccentRed
                    else -> c.TextPrimary
                }
            )
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

    /** 完整 adb 地址（ip 或 ip:port；port=0 表示无端口）——版本缓存查找与卡片主行共用 */
    val fullAddress: String
        get() = if (record.port == "0") record.ip else "${record.ip}:${record.port}"

    /** 设备是否已在线（在线 = state 为 device/offline，可执行 ADB 命令）。 */
    val isOnline: Boolean get() = online && (state == DeviceState.DEVICE || state == DeviceState.OFFLINE)

    val stateLabel: String
        get() = when (state) {
            DeviceState.DEVICE -> "在线"
            DeviceState.OFFLINE -> "离线"
            DeviceState.UNAUTHORIZED -> "未授权"
            else -> "未连接"
        }

    /** 状态色（绿=已连接 黄=离线 红=未连接/未授权；返回映射函数而非 @Composable getter） */
    fun stateColor(colors: com.easyadb.ui.designsystem.EasyAdbColors): Color = when (state) {
        DeviceState.DEVICE -> colors.StatusOnline
        DeviceState.OFFLINE -> colors.StatusOffline
        DeviceState.UNAUTHORIZED -> colors.StatusUnauthorized
        else -> colors.StatusUnauthorized
    }
}

/**
 * 从 [records]（数据库）与 [onlineDevices]（DeviceWatcher）合并出设备卡片数据。
 * 数据库中保存的设备始终展示（含离线），Watcher 推送的 state 驱动状态显示。
 * 排序：按使用率（连接次数）降序，次数相同按 IP·端口——应用启动时从库中
 * 读到的次数生效；运行中连接成功的计数只写库，下次启动生效（避免列表跳动）。
 */
private fun buildDeviceCards(
    records: List<DeviceRecord>,
    onlineDevices: List<DeviceInfo>
): List<DeviceCardData> {
    val stateByName = onlineDevices.associateBy { it.name }
    return records
        .sortedWith(
            compareByDescending<DeviceRecord> { it.connectCount }
                .thenBy { it.ip }
                .thenBy { it.port }
        )
        .map { record ->
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
 * 设备卡右键菜单项。对应 Python QMenu 的一个 QAction。
 *
 * @param iconVector 线稿图标（EasyAdbTileIcons，随主题染色），null 不显示图标。
 * @param danger 危险操作（如删除设备）：红系文案/图标，hover 红底（H5 .ctx-item.danger）。
 */
data class ContextMenuItem(
    val id: String,
    val name: String,
    val enabled: Boolean = true,
    val iconVector: ImageVector? = null,
    val danger: Boolean = false,
    val onClick: () -> Unit
)
