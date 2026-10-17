@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.functions

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.easyadb.core.adb.AppListLoader
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.TextInputFocusTracker
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import kotlinx.coroutines.delay
import kotlin.math.min

/** H5 .seg button{transition:.15s}：仅非选中项 hover 提亮渐变，选中切换瞬切（与 CommandsPanel 同口径） */
private const val SEG_TRANSITION_MS = 150

/** H5 .app-ops{transition:opacity .12s}：行悬浮时操作区浮现时长 */
private const val OPS_FADE_MS = 120

/** 行入场 rise：500ms / CSS ease 曲线（与 Tab 切换动效 tabSwitchTransition 同参数） */
private const val ROW_RISE_MS = 500

/** 行入场错峰：每行 45ms，封顶 400ms（H5 renderApps 的 animationDelay 公式） */
private const val ROW_RISE_STAGGER_MS = 45L
private const val ROW_RISE_STAGGER_CAP_MS = 400L

/** 类型筛选（对齐 H5 #appFilter seg：全部 / 第三方 / 系统应用） */
private enum class AppTypeFilter(val label: String) {
    ALL("全部"),
    THIRD_PARTY("第三方"),
    SYSTEM("系统应用")
}

/** 应用图标块色板（H5 演示数据的品牌色系；实际按包名哈希落色，仅作视觉区分） */
private val AppIconPalette = listOf(
    Color(0xFF3B82F6), Color(0xFF22C55E), Color(0xFFF97316), Color(0xFFEF4444),
    Color(0xFF6366F1), Color(0xFFFB7299), Color(0xFF10B981), Color(0xFFF59E0B),
    Color(0xFF14B8C8), Color(0xFF64748B)
)

/** 显示名：pm list packages -f 无应用名数据，取包名尾段（com.tencent.mm → mm） */
private fun displayNameOf(packageName: String): String =
    packageName.substringAfterLast('.').ifBlank { packageName }

/** 图标块底色：包名哈希映射色板，同名应用跨刷新颜色稳定 */
private fun iconColorOf(packageName: String): Color =
    AppIconPalette[Math.floorMod(packageName.hashCode(), AppIconPalette.size)]

/**
 * 应用管理面板（内容区「应用管理」Tab，SP-7001）。
 *
 * 对齐 H5 参照稿 pane-apps（doc/ui-reference/index.html）：
 * - 工具条卡：搜索框（名称/包名）+ seg 类型筛选（全部|第三方|系统应用）+
 *   「共 N 个应用」计数 + ghost 风格刷新按钮
 * - 应用行卡（38dp 高）：序号 / 22dp 彩色图标块 / 应用名 / 包名 / APK路径 / 悬浮操作区
 *   —— 第三方应用 = 「提取」mini 按钮；系统应用 = 「系统内置」标签（无操作，
 *   系统 APK 提取仍走文件传输 Tab 的提取应用弹窗）
 * - 空态文案与行入场 rise 动画（500ms/Ease、行距 45ms 封顶 400ms）均对齐 H5
 *
 * 数据由宿主持有（[AppListLoader.loadAppList]，pm list packages -f 仅包名/路径/类型）：
 * 进入 Tab 或设备切换时自动触发 [onLoadApps]（对齐弹窗「打开即加载」；AnimatedContent
 * 切走即销毁面板，切回重新加载）。字段增强（应用名/版本）见 SP-7001 后续演进。
 *
 * @param apps 宿主加载好的设备应用列表
 * @param isLoading 是否正在加载
 * @param selectedDeviceIp 当前选中设备 IP（未选设备时展示引导空态）
 * @param onLoadApps 加载/刷新应用列表（面板进入与设备切换时自动触发）
 * @param onExtractApk 提取应用 APK（宿主 FileDialog 选保存路径 + adb pull）
 */
@Composable
fun AppManagerPanel(
    apps: List<AppListLoader.AppInfo>,
    isLoading: Boolean,
    selectedDeviceIp: String?,
    onLoadApps: () -> Unit,
    onExtractApk: (AppListLoader.AppInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    // 进入 Tab 或设备变化时自动加载；未选设备由空态引导，不空跑 adb
    LaunchedEffect(selectedDeviceIp) {
        if (selectedDeviceIp != null) onLoadApps()
    }

    var searchText by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppTypeFilter.ALL) }

    // rise 错峰入场窗口：仅新数据会话（apps 实例变化）落地后的
    // 错峰上限 + 入场时长内组合的行播放动画；窗口外组合的行
    // （滚动进入视口、筛选/搜索重排、Tab 重进共享旧列表时滚动）直接呈现不重播
    val entranceDeadline = remember(apps) {
        if (apps.isEmpty()) 0L
        else System.currentTimeMillis() + ROW_RISE_STAGGER_CAP_MS + ROW_RISE_MS
    }

    val keyword = searchText.trim().lowercase()
    // 搜索匹配 名称+包名（H5 同逻辑）；「第三方」按弹窗口径含未知类型（type != 系统）
    val filteredApps = remember(apps, keyword, filter) {
        apps.filter { app ->
            val typeOk = when (filter) {
                AppTypeFilter.ALL -> true
                AppTypeFilter.THIRD_PARTY -> app.type != "系统"
                AppTypeFilter.SYSTEM -> app.type == "系统"
            }
            val textOk = keyword.isEmpty() ||
                displayNameOf(app.packageName).lowercase().contains(keyword) ||
                app.packageName.lowercase().contains(keyword)
            typeOk && textOk
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(easyAdbColors.Background)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 工具条卡：搜索 + seg 筛选 + 计数 + 刷新（对齐 H5 .apps-toolbar）──
        Surface(
            shape = easyAdbTokens.shapeLg,
            color = easyAdbColors.Surface,
            border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                SearchField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    modifier = Modifier
                        .weight(1f)
                        .widthIn(max = 420.dp)
                )
                // seg 分段筛选（对齐 H5 #appFilter，实现同 CommandsPanel 的 H5 口径）
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(easyAdbColors.SegTrack)
                        .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, RoundedCornerShape(10.dp))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    AppTypeFilter.entries.forEach { f ->
                        val active = f == filter
                        val segInteraction = remember { MutableInteractionSource() }
                        val segHovered by segInteraction.collectIsHoveredAsState()
                        val segBg = if (active) easyAdbColors.SegActiveBg else Color.Transparent
                        val segText by animateColorAsState(
                            targetValue = when {
                                active -> easyAdbColors.SegActiveText
                                segHovered -> easyAdbColors.TextPrimary
                                else -> easyAdbColors.TextSecondary
                            },
                            animationSpec = if (segHovered && !active) tween(SEG_TRANSITION_MS) else snap(),
                            label = "segText"
                        )
                        Text(
                            text = f.label,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = segText,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(segBg)
                                .clickable(
                                    interactionSource = segInteraction,
                                    indication = null
                                ) { filter = f }
                                .hoverable(segInteraction)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
                // 计数（H5 .apps-count margin-left:auto：右侧、刷新按钮旁）；
                // 占位 weight 吸收搜索框 420dp 上限之外的剩余宽度，把尾部推向右缘
                Spacer(Modifier.weight(1f))
                Text(
                    text = "共 ${filteredApps.size} 个应用",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = easyAdbColors.TextSecondary
                )
                GhostRefreshButton(onClick = onLoadApps)
            }
        }

        // ── 应用行列表（对齐 H5 .apps-list）──
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                selectedDeviceIp == null -> EmptyState(
                    icon = AppIcons.Android,
                    title = "未选择设备",
                    subtitle = "请在左侧设备列表选择设备后加载应用",
                    modifier = Modifier.fillMaxSize()
                )
                isLoading && apps.isEmpty() -> EmptyState(
                    icon = AppIcons.Refresh,
                    title = "正在加载应用列表...",
                    modifier = Modifier.fillMaxSize()
                )
                apps.isEmpty() -> EmptyState(
                    icon = AppIcons.Search,
                    title = "暂无应用",
                    subtitle = "未获取到设备应用，点击右上「刷新」重试",
                    modifier = Modifier.fillMaxSize()
                )
                filteredApps.isEmpty() -> EmptyState(
                    icon = AppIcons.Search,
                    title = "没有匹配的应用",
                    subtitle = "试试其他关键词或分类",
                    modifier = Modifier.fillMaxSize()
                )
                else -> {
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        itemsIndexed(filteredApps) { index, app ->
                            AppRow(
                                index = index,
                                app = app,
                                entranceDeadline = entranceDeadline,
                                onExtract = onExtractApk
                            )
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
    }
}

/**
 * 搜索框（对齐 H5 .search）：Field 底 + 1px 描边 + 左置放大镜图标，
 * 聚焦描边转主色；接入 TextInputFocusTracker（镜像 Tab 键盘透传的文本框聚焦守卫）。
 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(easyAdbColors.Field)
            .border(
                easyAdbTokens.strokeWidth,
                if (focused) easyAdbColors.Primary else easyAdbColors.Border,
                RoundedCornerShape(10.dp)
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppIcons.Search,
                contentDescription = null,
                tint = easyAdbColors.TextHint,
                modifier = Modifier.size(15.dp)
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged {
                        focused = it.hasFocus
                        TextInputFocusTracker.onFocusChanged(it.hasFocus)
                    }
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = "搜索应用名称或包名",
                        fontSize = 13.sp,
                        color = easyAdbColors.TextHint
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 13.sp,
                        color = easyAdbColors.TextPrimary
                    ),
                    cursorBrush = SolidColor(easyAdbColors.Primary),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** ghost 风格刷新按钮（对齐 H5 .btn-ghost + .mini-btn 语言：描边、悬浮提亮主色） */
@Composable
private fun GhostRefreshButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Surface(
        shape = easyAdbTokens.shapeMd,
        color = if (hovered) easyAdbColors.MiniBtnBgHover else easyAdbColors.MiniBtnBg,
        contentColor = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary,
        border = BorderStroke(
            easyAdbTokens.strokeWidth,
            if (hovered) easyAdbColors.PrimaryLight else easyAdbColors.Divider
        ),
        elevation = 0.dp,
        modifier = Modifier
            .clip(easyAdbTokens.shapeMd)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = AppIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Text(text = "刷新", fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * 应用行卡（对齐 H5 .app-row）：序号 / 彩色图标块 / 应用名 / 包名 / APK路径 / 悬浮操作区。
 * 行悬浮描边提亮主色，操作区随之浮现（120ms）；包名与路径列点击复制完整内容
 * （单行省略布局下拖选只能拿到可见部分）。行入场 rise（500ms/Ease、行距 45ms 封顶
 * 400ms）仅在 [entranceDeadline] 窗口内组合的行播放——滚动/筛选重排直接呈现。
 */
@Composable
private fun AppRow(
    index: Int,
    app: AppListLoader.AppInfo,
    entranceDeadline: Long,
    onExtract: (AppListLoader.AppInfo) -> Unit
) {
    val rowInteraction = remember { MutableInteractionSource() }
    val hovered by rowInteraction.collectIsHoveredAsState()

    // 入场窗口判定在行首次组合时快照（非状态订阅）：窗口内（新数据会话）播放 rise，
    // 窗口外（滚动进入视口等）直接呈现
    val playRise = remember { entranceDeadline > 0L && System.currentTimeMillis() < entranceDeadline }
    val rise = remember { Animatable(if (playRise) 0f else 1f) }
    LaunchedEffect(Unit) {
        if (rise.value < 1f) {
            delay(min(index * ROW_RISE_STAGGER_MS, ROW_RISE_STAGGER_CAP_MS))
            rise.animateTo(1f, tween(ROW_RISE_MS, easing = Ease))
        }
    }

    val opsAlpha by animateFloatAsState(
        targetValue = if (hovered) 1f else 0f,
        animationSpec = tween(OPS_FADE_MS),
        label = "opsAlpha"
    )
    val name = displayNameOf(app.packageName)
    val isSystem = app.type == "系统"
    val clipboard = LocalClipboardManager.current
    // 包名/路径静态色：TextHint 与 TextSecondary 的中间值（走查折中——TextHint 常显
    // 太淡、TextSecondary 常显偏深；hover 仍提亮到 TextSecondary 提示可点）
    val pkgPathResting = lerp(easyAdbColors.TextHint, easyAdbColors.TextSecondary, 0.5f)

    Surface(
        shape = easyAdbTokens.shapeLg,
        color = easyAdbColors.Surface,
        border = BorderStroke(
            easyAdbTokens.strokeWidth,
            if (hovered) easyAdbColors.PrimaryLight else easyAdbColors.Divider
        ),
        elevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .hoverable(rowInteraction)
            .graphicsLayer {
                alpha = rise.value
                translationY = (1f - rise.value) * 14f
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "${index + 1}",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.5.sp,
                color = easyAdbColors.TextHint,
                textAlign = TextAlign.End,
                modifier = Modifier.width(22.dp)
            )
            // 图标块：包名哈希底色 + 显示名首字母（无真实图标数据，仅视觉锚点）
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(easyAdbTokens.shapeMd)
                    .background(iconColorOf(app.packageName)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = name.first().uppercase(),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White
                )
            }
            Text(
                text = name,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = easyAdbColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(110.dp)
            )
            // 包名列（固定宽，点击复制完整包名）：悬浮提亮 + 手型光标提示可点
            val pkgInteraction = remember { MutableInteractionSource() }
            val pkgHovered by pkgInteraction.collectIsHoveredAsState()
            Text(
                text = app.packageName,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (pkgHovered) easyAdbColors.TextSecondary else pkgPathResting,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .width(220.dp)
                    .clip(easyAdbTokens.shapeSm)
                    .hoverable(pkgInteraction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = pkgInteraction, indication = null) {
                        clipboard.setText(AnnotatedString(app.packageName))
                    }
            )
            // APK 路径列（吃满剩余宽度，点击复制完整路径——单行省略下拖选拿不到全文）
            val pathInteraction = remember { MutableInteractionSource() }
            val pathHovered by pathInteraction.collectIsHoveredAsState()
            Text(
                text = app.apkPath,
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                color = if (pathHovered) easyAdbColors.TextSecondary else pkgPathResting,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .clip(easyAdbTokens.shapeSm)
                    .hoverable(pathInteraction)
                    .pointerHoverIcon(PointerIcon.Hand)
                    .clickable(interactionSource = pathInteraction, indication = null) {
                        clipboard.setText(AnnotatedString(app.apkPath))
                    }
            )
            // 悬浮操作区（H5 .app-ops）：系统应用附「系统内置」盾牌徽标；所有应用均可「提取」
            // （系统 APK 同一 adb pull 链路即可提取——走查决策，不沿用 H5 的系统行只读）
            Row(
                modifier = Modifier.graphicsLayer { alpha = opsAlpha },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (isSystem) {
                    SysBadgeButton(tooltip = "系统内置 · 受系统保护，不可卸载")
                }
                ExtractMiniButton(onClick = { onExtract(app) })
            }
        }
    }
}

/** 「提取」mini 按钮（对齐 H5 .app-ops .mini-btn：26dp 高 + 输入框同款 Border 描边，未悬停也清晰可辨；悬浮提亮主色） */
@Composable
private fun ExtractMiniButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .height(26.dp)
            .clip(easyAdbTokens.shapeLg)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(if (hovered) easyAdbColors.MiniBtnBgHover else easyAdbColors.MiniBtnBg)
            .border(
                easyAdbTokens.strokeWidth,
                if (hovered) easyAdbColors.PrimaryLight else easyAdbColors.Border,
                easyAdbTokens.shapeLg
            )
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "提取",
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
        )
    }
}

/**
 * 系统内置徽标（对齐 H5 .sys-badge）：盾牌+对勾图标替代文字标签，28×23dp 圆角底，
 * 悬浮提亮（底色/描边加深、图标转亮蓝）；悬停 ≥300ms 弹出释义气泡。
 * 气泡向左伸展（CenterEnd + 负偏移）——徽标位于行尾，向右弹会溢出窗口右缘；
 * CMP 1.7.3 无 TooltipArea，Box + Popup 自建（同 LogcatPanel TooltipIconButton）。
 */
@Composable
private fun SysBadgeButton(tooltip: String) {
    var hovered by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(300)
            showTip = true
        } else {
            showTip = false
        }
    }
    Box {
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(23.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(
                    if (hovered) easyAdbColors.AccentSky.copy(alpha = 0.20f)
                    else easyAdbColors.AccentSky.copy(alpha = 0.10f)
                )
                .border(
                    easyAdbTokens.strokeWidth,
                    if (hovered) easyAdbColors.AccentSky.copy(alpha = 0.50f)
                    else easyAdbColors.AccentSky.copy(alpha = 0.25f),
                    RoundedCornerShape(7.dp)
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppIcons.ShieldCheck,
                contentDescription = "系统内置",
                tint = if (hovered) easyAdbColors.PrimaryLight else easyAdbColors.AccentSky,
                modifier = Modifier.size(15.dp)
            )
        }
        if (showTip) {
            // 徽标宽 28dp + 8dp 间隙：气泡右缘落在徽标左侧，向左伸展覆盖路径列空白区
            val offsetX = with(density) { -(36.dp).roundToPx() }
            Popup(alignment = Alignment.CenterEnd, offset = IntOffset(offsetX, 0)) {
                Surface(
                    shape = easyAdbTokens.shapeSm,
                    elevation = easyAdbTokens.elevationMid,
                    color = easyAdbColors.TooltipBackground
                ) {
                    Text(
                        text = tooltip,
                        color = easyAdbColors.TooltipText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
