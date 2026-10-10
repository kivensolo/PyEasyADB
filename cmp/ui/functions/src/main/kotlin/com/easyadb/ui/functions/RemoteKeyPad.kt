@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.FunctionItem
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.GradientButton
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：按键模拟面板（对齐 HTML 参照稿按键模拟 Tab 最新双栏布局）。
 *              左栏遥控器卡（固定 400dp）：电源/静音胶囊键、3×3 D-pad 方向十字
 *              （中央 OK 圆形品牌渐变键，方向键深色渐变底）、返回/主页（渐变
 *              圆形）/菜单系统键行、底部提示文字；右栏两张卡：音量卡（本地模拟
 *              音量条 ±5，静音时进度归零并显示「静音」，与 HTML 稿 renderVol
 *              行为一致）与自定义按键事件卡（KEYCODE 数值输入，支持 "+ " 拼接
 *              逐条发送，间隔 200ms，经 [onCustomKey] 回调由宿主执行）。
 *              全部遥控器按键仍按 XML function_templates.xml 的功能项经
 *              [onKeyClick] 走 input keyevent 执行链路；音量为纯本地演示模拟
 *              （同 HTML 稿），不回读设备真实状态。
 */
@Composable
fun RemoteKeyPad(
    items: List<FunctionItem>,
    onKeyClick: (FunctionItem) -> Unit,
    onCustomKey: (List<Int>) -> Unit = {},
    modifier: Modifier = Modifier
) {
    // 按 XML 文案定位各键位功能项；缺失的键位不渲染（配置驱动的健壮性）
    val keyMap = remember(items) { items.associateBy { it.text } }
    fun key(name: String): FunctionItem? = keyMap[name]

    // 本地模拟状态（同 HTML 稿：音量 60% 起 ±5、静音联动音量条归零）
    var volume by remember { mutableStateOf(60) }
    var muted by remember { mutableStateOf(false) }

    // 自定义按键事件输入与校验错误（同 HTML 稿 sendCustom 逻辑）
    var customInput by remember { mutableStateOf("") }
    var customError by remember { mutableStateOf<String?>(null) }

    fun sendCustom() {
        val raw = customInput.trim()
        if (raw.isEmpty()) {
            customError = "请输入键值"
            return
        }
        val parts = raw.split("+")
        for (part in parts) {
            if (!Regex("^\\d{1,4}$").matches(part.trim())) {
                customError = "键值格式不正确：\"${part.trim()}\" 不是有效数字"
                return
            }
        }
        customError = null
        onCustomKey(parts.map { it.trim().toInt() })
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top
    ) {
        // ── 左栏：遥控器卡 ──
        Surface(
            modifier = Modifier.width(400.dp),
            shape = easyAdbTokens.shapeXl,
            color = easyAdbColors.Surface,
            border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
            elevation = 0.dp
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 顶行：电源（带线稿图标）+ 静音（文字随状态切换，点击翻转静音态）
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    key("电源键")?.let { item ->
                        PillKey(item = item, onClick = onKeyClick) {
                            Icon(
                                imageVector = EasyAdbTileIcons.Power,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = easyAdbColors.TextSecondary
                            )
                            Spacer(Modifier.width(7.dp))
                            KeyLabel("电源")
                        }
                    }
                    key("静音键")?.let { item ->
                        PillKey(item = item, onClick = onKeyClick, onLocalState = { muted = !muted }) {
                            KeyLabel(if (muted) "取消静音" else "静音")
                        }
                    }
                }

                // 3×3 D-pad 方向十字
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DpadRow {
                        EmptyDpadCell()
                        key("上键")?.let { DirectionKey(item = it, label = "▲", onClick = onKeyClick) }
                            ?: EmptyDpadCell()
                        EmptyDpadCell()
                    }
                    DpadRow {
                        key("左键")?.let { DirectionKey(item = it, label = "◀", onClick = onKeyClick) }
                            ?: EmptyDpadCell()
                        key("确认键")?.let { OkKey(item = it, onClick = onKeyClick) } ?: EmptyDpadCell()
                        key("右键")?.let { DirectionKey(item = it, label = "▶", onClick = onKeyClick) }
                            ?: EmptyDpadCell()
                    }
                    DpadRow {
                        EmptyDpadCell()
                        key("下键")?.let { DirectionKey(item = it, label = "▼", onClick = onKeyClick) }
                            ?: EmptyDpadCell()
                        EmptyDpadCell()
                    }
                }

                // 底行：返回 / 主页（渐变圆形）/ 菜单
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    key("返回键")?.let { SystemKey(item = it, label = "返回", onClick = onKeyClick) }
                        ?: Spacer(Modifier.width(1.dp))
                    key("主页键")?.let { SystemKey(item = it, label = "主页", onClick = onKeyClick, isRound = true) }
                        ?: Spacer(Modifier.width(1.dp))
                    key("菜单键")?.let { SystemKey(item = it, label = "菜单", onClick = onKeyClick) }
                        ?: Spacer(Modifier.width(1.dp))
                }

                Text(
                    text = "按键通过 adb shell input keyevent 发送到当前设备",
                    fontSize = 11.5.sp,
                    color = easyAdbColors.TextHint
                )
            }
        }

        // ── 右栏：音量卡 + 自定义按键事件卡 ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = easyAdbTokens.shapeLg,
                color = easyAdbColors.Surface,
                border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    SectionHead("音量")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // 音量减：H5 行为——只减数值，不解除静音
                        key("音量减")?.let { item ->
                            BigKey(item = item, label = "−", onClick = onKeyClick) {
                                volume = (volume - 5).coerceAtLeast(0)
                            }
                        }
                        // 音量条：本地模拟进度（静音时归零，同 HTML renderVol）
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(easyAdbColors.SurfaceVariant)
                                .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, RoundedCornerShape(6.dp))
                        ) {
                            val fill = if (muted) 0 else volume
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fill / 100f)
                                    .height(10.dp)
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(easyAdbColors.Primary, easyAdbColors.PrimaryLight)
                                        )
                                    )
                            )
                        }
                        Text(
                            text = if (muted) "静音" else "$volume%",
                            fontSize = 13.sp,
                            color = easyAdbColors.PrimaryLight,
                            modifier = Modifier.width(48.dp),
                            textAlign = TextAlign.End
                        )
                        // 音量加：H5 行为——先解除静音再加数值
                        key("音量加")?.let { item ->
                            BigKey(item = item, label = "＋", onClick = onKeyClick) {
                                if (muted) muted = false
                                volume = (volume + 5).coerceAtMost(100)
                            }
                        }
                    }
                }
            }

            // 自定义按键事件卡（同 HTML custom-card）：KEYCODE 输入 + 发送
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = easyAdbTokens.shapeLg,
                color = easyAdbColors.Surface,
                border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
                elevation = 0.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
                    SectionHead("自定义按键事件")
                    Spacer(Modifier.height(12.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CustomKeycodeInput(
                            value = customInput,
                            onValueChange = {
                                customInput = it
                                customError = null
                            },
                            onEnter = { sendCustom() },
                            modifier = Modifier.weight(1f)
                        )
                        GradientButton(text = "发送", onClick = { sendCustom() })
                    }
                    Spacer(Modifier.height(11.dp))
                    Text(
                        text = "键值通过 \"+\" 拼接可逐条发送，间隔 200ms",
                        fontSize = 11.5.sp,
                        fontFamily = FontFamily.Monospace,
                        color = easyAdbColors.TextHint
                    )
                    if (customError != null) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = customError!!,
                            fontSize = 11.5.sp,
                            color = easyAdbColors.LogError
                        )
                    }
                }
            }
        }
    }
}

/** 3×3 D-pad 单行（等宽 64dp 格）。 */
@Composable
private fun DpadRow(content: @Composable () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        content()
    }
}

/** D-pad 空占位格（保持十字网格对齐）。 */
@Composable
private fun EmptyDpadCell() {
    Spacer(modifier = Modifier.size(64.dp))
}

/** 分区标题（音量/自定义按键事件）。 */
@Composable
private fun SectionHead(text: String) {
    Text(
        text = text,
        fontSize = 13.5.sp,
        fontWeight = FontWeight.Bold,
        color = easyAdbColors.TextSecondary,
        letterSpacing = 0.5.sp
    )
}

/** 遥控器胶囊/系统键文字样式。 */
@Composable
private fun KeyLabel(text: String) {
    Text(text = text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextSecondary)
}

/** 键位通用 Surface：描边 + hover 描边变主色 + 按压底色反馈（H5 :active 语言）。 */
@Composable
private fun KeySurface(
    item: FunctionItem,
    onClick: (FunctionItem) -> Unit,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(12.dp),
    border: BorderStroke? = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Border),
    background: Color = easyAdbColors.Hover,
    backgroundBrush: Brush? = null,
    onLocalState: () -> Unit = {},
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val resolvedBorder = when {
        isHovered -> BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Primary)
        else -> border
    }

    Surface(
        onClick = {
            onLocalState()
            onClick(item)
        },
        enabled = item.state != "disable",
        interactionSource = interactionSource,
        modifier = modifier,
        shape = shape,
        color = if (backgroundBrush != null) Color.Transparent else background,
        border = resolvedBorder,
        elevation = 0.dp
    ) {
        Box(
            modifier = when {
                // 渐变底 + 按压时叠加半透明选中色，保留按压反馈（无 graphicsLayer 缩放）
                backgroundBrush != null && isPressed ->
                    Modifier.background(backgroundBrush).background(easyAdbColors.Selected.copy(alpha = 0.4f))
                backgroundBrush != null -> Modifier.background(backgroundBrush)
                else -> Modifier
            },
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

/** 顶部胶囊键（电源/静音）：圆角 999 胶囊，内容自定（图标+文字），可附带本地状态回调。 */
@Composable
private fun PillKey(
    item: FunctionItem,
    onClick: (FunctionItem) -> Unit,
    onLocalState: () -> Unit = {},
    content: @Composable () -> Unit
) {
    KeySurface(
        item = item,
        onClick = onClick,
        onLocalState = onLocalState,
        modifier = Modifier.height(36.dp),
        shape = CircleShape
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            content()
        }
    }
}

/** D-pad 方向键：64dp 圆角方块 + 深色渐变底（H5 .dk），三角文字符号。 */
@Composable
private fun DirectionKey(
    item: FunctionItem,
    label: String,
    onClick: (FunctionItem) -> Unit
) {
    KeySurface(
        item = item,
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = RoundedCornerShape(14.dp),
        backgroundBrush = keyGradientBrush()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = easyAdbColors.TextSecondary
        )
    }
}

/** D-pad 中央确认键：64dp 圆形品牌渐变 + 白字 OK。 */
@Composable
private fun OkKey(
    item: FunctionItem,
    onClick: (FunctionItem) -> Unit
) {
    KeySurface(
        item = item,
        onClick = onClick,
        modifier = Modifier.size(64.dp),
        shape = CircleShape,
        border = null,
        backgroundBrush = Brush.verticalGradient(
            listOf(easyAdbColors.Primary, easyAdbColors.PrimaryDark)
        )
    ) {
        Text(
            text = "OK",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            color = easyAdbColors.TextOnPrimary
        )
    }
}

/** 遥控器底部系统键（返回/主页/菜单）：内容自适应宽，主页键为渐变圆形（H5 .rk-round）。 */
@Composable
private fun SystemKey(
    item: FunctionItem,
    label: String,
    onClick: (FunctionItem) -> Unit,
    isRound: Boolean = false
) {
    KeySurface(
        item = item,
        onClick = onClick,
        modifier = Modifier.height(40.dp),
        shape = if (isRound) CircleShape else RoundedCornerShape(12.dp),
        backgroundBrush = if (isRound) keyGradientBrush() else null
    ) {
        Box(modifier = Modifier.padding(horizontal = if (isRound) 18.dp else 22.dp)) {
            KeyLabel(label)
        }
    }
}

/** 键位深色渐变底（H5 .dk/.rk-round 的 180deg 渐变，明暗主题均从调色板派生）。 */
@Composable
private fun keyGradientBrush(): Brush =
    Brush.verticalGradient(listOf(easyAdbColors.Hover, easyAdbColors.PanelHeader))

/** 音量大键（− ＋）：等宽圆角方块，附带本地模拟状态回调。 */
@Composable
private fun BigKey(
    item: FunctionItem,
    label: String,
    onClick: (FunctionItem) -> Unit,
    onLocalState: () -> Unit
) {
    KeySurface(
        item = item,
        onClick = onClick,
        onLocalState = onLocalState,
        modifier = Modifier
            .width(64.dp)
            .height(40.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = easyAdbColors.TextSecondary
        )
    }
}

/**
 * 自定义 KEYCODE 输入框：mono 字体 + 圆角描边 + 空值 placeholder，回车触发发送。
 * （同 HTML custom-row input 样式）
 */
@Composable
private fun CustomKeycodeInput(
    value: String,
    onValueChange: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, RoundedCornerShape(10.dp))
            .background(easyAdbColors.SurfaceVariant, RoundedCornerShape(10.dp))
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && (event.key == Key.Enter || event.key == Key.NumPadEnter)) {
                    onEnter()
                    true
                } else {
                    false
                }
            }
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = "输入 KEYCODE 数值，如 62 或 62+62+66",
                fontSize = 13.sp,
                color = easyAdbColors.TextHint,
                maxLines = 1
            )
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = easyAdbColors.TextPrimary
            ),
            cursorBrush = SolidColor(easyAdbColors.Primary),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

