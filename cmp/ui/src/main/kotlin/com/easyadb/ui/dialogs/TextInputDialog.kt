package com.easyadb.ui.dialogs

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.FieldShakeState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbMonospace
import com.easyadb.ui.designsystem.observeFrame

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/27
 * description：文本输入对话框（风格对齐视频录制/屏幕截图弹窗）：
 *              独立 DialogWindow 原生标题栏窗口（标题「文本输入」），弹出时主窗口
 *              背景不变暗、无遮罩层。内容沿用 H5 #modalTextInput 布局：label +
 *              多行 textarea（H5 .ti-textarea：等宽 13sp、行高 1.55、min 120dp
 *              超出滚动、focus 蓝描边、打开即自动聚焦），底部「清空」靠左
 *              （btn-plain，清空文本），右侧取消（btn-plain）+「输入」（btn-primary
 *              蓝渐变投影）；空文本点「输入」时输入框抖动 + 聚焦（复用
 *              FieldShakeState，H5 为 toast 提示）。确认后回调 [onInput]，宿主负责
 *              执行 `adb shell input text`（含换行/空格转义）。
 */
@Composable
fun TextInputDialog(
    onDismiss: () -> Unit,
    onInput: (text: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    // 弹窗内独立抖动状态：空文本点「输入」时输入框红边晃动（H5 为 toast，Compose 统一抖动反馈）
    val textShake = remember { FieldShakeState() }
    val dialogState = rememberDialogState(width = 440.dp, height = 300.dp)

    fun trySend() {
        if (text.trim().isEmpty()) {
            textShake.shake()
            return
        }
        onInput(text)
    }

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "文本输入",
        resizable = false
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = easyAdbColors.Surface) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 22.dp, end = 22.dp, top = 16.dp, bottom = 20.dp)
            ) {
                // H5 .modal-body：label + 多行 textarea
                TextInputField(
                    value = text,
                    onValueChange = { text = it },
                    shakeState = textShake
                )
                Spacer(modifier = Modifier.weight(1f))
                // H5 .modal-foot：清空靠左（margin-right:auto），右对齐 取消/输入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    DialogPlainButton(text = "清空", onClick = { text = "" })
                    Spacer(modifier = Modifier.weight(1f))
                    DialogPlainButton(text = "取消", onClick = onDismiss)
                    Spacer(modifier = Modifier.width(10.dp))
                    DialogPrimaryButton(text = "输入", onClick = { trySend() })
                }
            }
        }
    }
}

// ───────────────────────── 多行文本输入框（H5 .ti-textarea + 抖动） ─────────────────────────

/**
 * 多行文本输入框：label「输入要发送到设备的文本（支持多行）」+ 等宽 13sp（行高 1.55）、
 * min 120dp 超出纵向滚动、10dp 圆角、focus 蓝描边；[shakeState] 触发时
 * 红边晃动 + 聚焦（与卸载确认弹窗 PkgField 同款抖动规格）。打开即自动聚焦（H5 同款）。
 */
@Composable
private fun TextInputField(
    value: String,
    onValueChange: (String) -> Unit,
    shakeState: FieldShakeState
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val shakeFrame = shakeState.observeFrame(focusRequester)
    val shakeRed = easyAdbColors.AccentRed
    val fieldShape = RoundedCornerShape(10.dp)

    // 打开即聚焦（H5 openTextInputModal 延时 240ms focus；requester 未挂载时防御）
    LaunchedEffect(Unit) {
        runCatching { focusRequester.requestFocus() }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "输入要发送到设备的文本（支持多行）",
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.TextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp)
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
                .padding(horizontal = 13.dp, vertical = 10.dp)
                .onFocusChanged { isFocused = it.hasFocus },
            contentAlignment = Alignment.TopStart
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = TextStyle(
                    fontFamily = easyAdbMonospace,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                    color = easyAdbColors.TextPrimary
                ),
                cursorBrush = SolidColor(easyAdbColors.Primary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 100.dp)
                    .verticalScroll(rememberScrollState())
                    .focusRequester(focusRequester)
            )
            if (value.isEmpty()) {
                Text(
                    text = "输入要发送的文本",
                    fontFamily = easyAdbMonospace,
                    fontSize = 13.sp,
                    color = easyAdbColors.TextHint
                )
            }
        }
    }
}

// ───────────────────────── 底部按钮（H5 .btn-plain / .btn-primary） ─────────────────────────

/** 次级按钮（清空/取消），对齐 H5 .btn-plain：MiniBtnBg 底 + Border 描边，hover 字色转亮 */
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
 * 主操作按钮（输入），对齐 H5 .btn + .btn-primary：14px/600 白字、9/20 padding
 * （总高约 38dp）、10px 圆角、#3f88f8→#2563eb 垂直渐变 + 蓝色投影（明暗同值）。
 * 规格与 ScreenshotDialog.SsPrimaryButton 一致（.btn 为 modal 通用样式）。
 */
@Composable
private fun DialogPrimaryButton(text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .shadow(elevation = 4.dp, shape = shape, clip = false, ambientColor = PrimaryShadow, spotColor = PrimaryShadow)
            .background(Brush.verticalGradient(listOf(PrimaryGradTop, PrimaryGradBottom)), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White, letterSpacing = 0.5.sp)
    }
}

/** .btn-primary 渐变与投影固定色（H5 明暗同值硬编码） */
private val PrimaryGradTop = Color(0xFF3F88F8)
private val PrimaryGradBottom = Color(0xFF2563EB)
private val PrimaryShadow = Color(0x612563EB)
