package com.easyadb.ui.dialogs

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.AppListLoader
import com.easyadb.ui.designsystem.FieldShakeState
import com.easyadb.ui.designsystem.TextInputFocusTracker
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.observeFrame

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWang
 * date：2026/10/23
 * description：卸载应用确认对话框（对齐 H5 #modalUninstall 的主窗口内浮层）：
 *              全窗 ModalScrim 遮罩（点击关闭）+ 居中 440dp 卡片（ModalBg 渐变 +
 *              1px 边框 + 18dp 圆角）。包名输入框预填参数区包名；下方第三方应用
 *              chips 快速选择（H5 .pick-chips，仅列可卸载的非系统应用，限高滚动
 *              ——真实设备应用数会撑爆 440dp 弹窗，超出 H5 演示稿的实用处理）；
 *              「无法撤销」红色警示文案（H5 .un-warn）。底部取消（btn-plain）+
 *              卸载（btn-danger 红渐变投影，hover 提亮）。包名为空点「卸载」时
 *              输入框抖动 + 聚焦（复用 FieldShakeState，与参数卡缺参反馈同款；
 *              H5 为 focus + toast）。挂载约定同 NewConnectDialog：主窗口满窗
 *              Box 最后一层，聚焦上报 TextInputFocusTracker 防键盘接管。
 */
@Composable
fun UninstallConfirmDialog(
    initialPackage: String,
    deviceApps: List<AppListLoader.AppInfo>,
    isLoadingApps: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (packageName: String) -> Unit
) {
    var pkgText by remember { mutableStateOf(initialPackage) }
    // 弹窗内独立抖动状态：空包名点「卸载」时输入框红边晃动（H5 为 focus+toast，Compose 统一抖动反馈）
    val pkgShake = remember { FieldShakeState() }

    // 浮层存续期间键盘本地优先（同 NewConnectDialog：防实时预览键盘接管抢键）
    DisposableEffect(Unit) {
        TextInputFocusTracker.onFocusChanged(true)
        onDispose { TextInputFocusTracker.onFocusChanged(false) }
    }

    fun tryConfirm() {
        val pkg = pkgText.trim()
        if (pkg.isEmpty()) {
            pkgShake.shake()
            return
        }
        onConfirm(pkg)
    }

    // H5 .modal-backdrop 遮罩点击关闭；卡片拦截点击防穿透
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(easyAdbColors.ModalScrim)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
    ) {
        val modalShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .width(440.dp)
                .background(
                    Brush.verticalGradient(listOf(easyAdbColors.ModalBgTop, easyAdbColors.ModalBgBottom)),
                    modalShape
                )
                .border(1.dp, easyAdbColors.Border, modalShape)
                .clickable(interactionSource = cardInteraction, indication = null) {},
        ) {
            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                // H5 .modal-head：标题 16px/700 + 30×30 关闭钮
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "卸载应用",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = easyAdbColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ModalCloseButton(onClick = onDismiss)
                }
                // H5 .modal-body：padding 16 22 4、块间 gap 13px
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    // H5 .mfield：label + 等宽输入框（40dp 高、focus 蓝描边），接缺参抖动
                    PkgField(
                        value = pkgText,
                        onValueChange = { pkgText = it },
                        shakeState = pkgShake
                    )
                    PickChips(
                        apps = deviceApps,
                        isLoading = isLoadingApps,
                        onPick = { pkgText = it }
                    )
                    // H5 .un-warn：12.5sp/1.6 行高 TextSecondary，「无法撤销」加粗
                    Text(
                        text = "卸载后将删除该应用及其全部数据，且无法撤销。请确认包名无误。",
                        fontSize = 12.5.sp,
                        lineHeight = 20.sp,
                        color = easyAdbColors.TextSecondary
                    )
                }
                // H5 .modal-foot：右对齐 取消/卸载，padding 18 22 20
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    DialogPlainButton(text = "取消", onClick = onDismiss)
                    DialogDangerButton(text = "卸载", onClick = { tryConfirm() })
                }
            }
        }
    }
}

// ───────────────────────── 包名输入框（H5 .mfield + 缺参抖动） ─────────────────────────

/**
 * 包名输入框：label「输入包名，或从常用应用中选择」+ 等宽 13sp、高 40dp、10dp 圆角、
 * focus 蓝描边；[shakeState] 触发时红边晃动 + 聚焦（与 AppParamPanel.CompactTextField
 * 同款抖动规格，输入框规格不同不提取）。
 */
@Composable
private fun PkgField(
    value: String,
    onValueChange: (String) -> Unit,
    shakeState: FieldShakeState
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shakeFrame = shakeState.observeFrame(focusRequester)
    val shakeRed = easyAdbColors.AccentRed
    val fieldShape = RoundedCornerShape(10.dp)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "输入包名，或从常用应用中选择",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.TextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .graphicsLayer { translationX = shakeFrame.translationXPx }
                .border(
                    width = 1.dp,
                    color = when {
                        shakeFrame.redEdge -> shakeRed
                        isFocused -> easyAdbColors.Primary
                        else -> easyAdbColors.Border
                    },
                    shape = fieldShape
                )
                .background(easyAdbColors.Field, fieldShape)
                .clip(fieldShape)
                .drawBehind {
                    val overlay = shakeFrame.redOverlayAlpha
                    if (overlay > 0f) drawRect(color = shakeRed.copy(alpha = overlay))
                }
                .padding(horizontal = 13.dp)
                .onFocusChanged {
                    isFocused = it.hasFocus
                    TextInputFocusTracker.onFocusChanged(it.hasFocus)
                },
            contentAlignment = Alignment.CenterStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = easyAdbColors.TextPrimary
                ),
                cursorBrush = SolidColor(easyAdbColors.Primary),
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
            if (value.isEmpty()) {
                Text(
                    text = "com.example.app",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = easyAdbColors.TextHint
                )
            }
        }
    }
}

// ───────────────────────── 应用 chips（H5 .pick-chips） ─────────────────────────

/**
 * 第三方应用包名 chips：等宽 11.5sp、7dp 圆角、hover 蓝边提亮；限高约 4 行滚动。
 * 加载中/无数据时显示提示行（H5 演示数据恒有 chips，此处为真实设备的空态兜底）。
 * 仅列可卸载的非系统应用（H5 type==='user' 口径，同 AppManagerPanel「第三方」筛选）。
 */
@Composable
private fun PickChips(
    apps: List<AppListLoader.AppInfo>,
    isLoading: Boolean,
    onPick: (String) -> Unit
) {
    val userApps = apps.filter { it.type != "系统" }
    Column(modifier = Modifier.heightIn(max = 148.dp).verticalScroll(rememberScrollState())) {
        when {
            isLoading -> Text(
                text = "正在加载设备应用列表...",
                fontSize = 11.5.sp,
                color = easyAdbColors.TextHint
            )
            userApps.isEmpty() -> Text(
                text = "未获取到可卸载的应用，可直接输入包名",
                fontSize = 11.5.sp,
                color = easyAdbColors.TextHint
            )
            else -> FlowChips(packages = userApps.map { it.packageName }, onPick = onPick)
        }
    }
}

/** chips 流式布局（flex wrap 语义）：FlowRow 行满自动折行，行/列间距 7px 同 H5 gap。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChips(packages: List<String>, onPick: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        packages.forEach { pkg ->
            PkgChip(pkg = pkg, onClick = { onPick(pkg) })
        }
    }
}

/** 单个包名 chip（H5 .pick-chips button：mono 11.5sp、7dp 圆角、hover 蓝边提亮）。 */
@Composable
private fun PkgChip(pkg: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Text(
        text = pkg,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.5.sp,
        color = if (hovered) easyAdbColors.ChipHoverText else easyAdbColors.ChipText,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(7.dp))
            .hoverable(interaction)
            .background(if (hovered) easyAdbColors.ChipHoverBg else easyAdbColors.ChipBg)
            .border(
                1.dp,
                if (hovered) easyAdbColors.Primary else easyAdbColors.ChipBorder,
                RoundedCornerShape(7.dp)
            )
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

// ───────────────────────── 头部关闭钮（H5 .modal-x，同 NewConnectDialog 规格） ─────────────────────────

/** 30×30、8dp 圆角，hover 亮底 ModalXHoverBg + 图标转亮 */
@Composable
private fun ModalCloseButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .hoverable(interaction)
            .background(
                if (hovered) easyAdbColors.ModalXHoverBg else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "关闭",
            tint = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary,
            modifier = Modifier.size(14.dp)
        )
    }
}

// ───────────────────────── 底部按钮（H5 .btn-plain / .btn-danger） ─────────────────────────

/** 次级按钮（取消），对齐 H5 .btn-plain：MiniBtnBg 底 + Border 描边，hover 字色转亮 */
@Composable
private fun DialogPlainButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(
        modifier = Modifier
            .hoverable(interaction)
            .background(easyAdbColors.MiniBtnBg, RoundedCornerShape(10.dp))
            .border(1.dp, easyAdbColors.Border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.5.sp,
            color = if (hovered) easyAdbColors.TextPrimary else easyAdbColors.TextSecondary
        )
    }
}

/**
 * 危险操作按钮（卸载），对齐 H5 .btn-danger：#ef4444→#dc2626 垂直渐变 + 红色投影
 * （rgba(220,38,38,.35)），hover 过渡到 #f05252→#e02f2f（H5 transition .16s）。
 */
@Composable
private fun DialogDangerButton(text: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val gradTop by animateColorAsState(
        targetValue = if (hovered) DangerHoverTop else DangerGradTop,
        animationSpec = tween(BUTTON_TRANSITION_MS),
        label = "dangerTop"
    )
    val gradBottom by animateColorAsState(
        targetValue = if (hovered) DangerHoverBottom else DangerGradBottom,
        animationSpec = tween(BUTTON_TRANSITION_MS),
        label = "dangerBottom"
    )
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .shadow(elevation = 4.dp, shape = shape, clip = false, ambientColor = DangerShadow, spotColor = DangerShadow)
            .background(Brush.verticalGradient(listOf(gradTop, gradBottom)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.5.sp)
    }
}

/** .btn-danger 渐变/投影固定色（H5 明暗同值硬编码）与 hover 过渡时长 */
private val DangerGradTop = Color(0xFFEF4444)
private val DangerGradBottom = Color(0xFFDC2626)
private val DangerHoverTop = Color(0xFFF05252)
private val DangerHoverBottom = Color(0xFFE02F2F)
private val DangerShadow = Color(0x59DC2626)
private const val BUTTON_TRANSITION_MS = 160
