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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.util.NetworkUtils
import com.easyadb.ui.designsystem.TextInputFocusTracker
import com.easyadb.ui.designsystem.easyAdbColors

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/22
 * description：连接新设备对话框（对齐 H5 #modalDevice 的主窗口内浮层，非独立 OS 窗口）：
 *              全窗 ModalScrim 遮罩（点击关闭）+ 居中 440dp 卡片（ModalBg 渐变 + 1px 边框 +
 *              18dp 圆角 + 头部标题/关闭钮）。三字段——设备名称（可选，留空连接时保留库中
 *              旧别名）/ IP 地址（必填，NetworkUtils 校验）/ 端口（缺省 5555，校验 1-65535）。
 *              校验失败在字段区下方内联红字提示（H5 为 toast，Compose 端无全局 toast）；
 *              「连接」回调携带名称与拼好的 ip:port，宿主负责 adb connect 与设备入库
 *              （名称写 alias，设备卡即显示该命名）。
 *              挂载约定：需置于主窗口满窗 Box 的最后一层（盖在内容之上），且组合期间向
 *              TextInputFocusTracker 上报聚焦（浮层在主窗口内，须防实时预览键盘接管抢键）。
 */
@Composable
fun NewConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (name: String, deviceIp: String) -> Unit
) {
    var nameText by remember { mutableStateOf("") }
    var ipText by remember { mutableStateOf("") }
    var portText by remember { mutableStateOf("5555") }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // 浮层存续期间键盘本地优先（弹窗打开时按键不得被实时预览接管转投 scrcpy）；
    // 字段级上报见 MField，此处兜底未聚焦字段时的窗口级按键
    DisposableEffect(Unit) {
        TextInputFocusTracker.onFocusChanged(true)
        onDispose { TextInputFocusTracker.onFocusChanged(false) }
    }

    fun tryConnect() {
        val ip = ipText.trim()
        if (!NetworkUtils.isIpMatches(ip)) {
            errorMsg = "IP 地址格式不正确"
            return
        }
        val port = portText.trim().ifEmpty { "5555" }
        val portNum = port.toIntOrNull()
        if (portNum == null || portNum !in 1..65535) {
            errorMsg = "端口无效（1-65535）"
            return
        }
        onConnect(nameText.trim(), "$ip:$port")
    }

    // H5 .modal-backdrop：rgba(4,7,13,.62) 全窗遮罩，点击关闭
    //（backdrop-filter: blur(5px) 在 Compose 无对应 API，不复刻）；卡片拦截点击防穿透
    val scrimInteraction = remember { MutableInteractionSource() }
    val cardInteraction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(easyAdbColors.ModalScrim)
            .clickable(interactionSource = scrimInteraction, indication = null, onClick = onDismiss)
    ) {
        // H5 .modal：440 宽卡片居中，高度随内容（无固定窗口高度，按钮行永不被裁）
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
                // H5 .modal-head：padding 18/22/0，标题 16px/700 + 关闭钮 30×30
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "连接新设备",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = easyAdbColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    ModalCloseButton(onClick = onDismiss)
                }
                // H5 .modal-body：padding 16 22 4、字段间 gap 13px
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(13.dp)
                ) {
                    MField(
                        label = "设备名称",
                        value = nameText,
                        onValueChange = {
                            nameText = it
                            errorMsg = null
                        },
                        placeholder = "例如：书房电视"
                    )
                    // H5 .mrow：IP weight(1f) + 端口固定 120px，gap 12px
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(modifier = Modifier.weight(1f)) {
                            MField(
                                label = "IP 地址",
                                value = ipText,
                                onValueChange = {
                                    ipText = it
                                    errorMsg = null
                                },
                                placeholder = "192.168.1.120"
                            )
                        }
                        Box(modifier = Modifier.width(120.dp)) {
                            MField(
                                label = "端口",
                                value = portText,
                                onValueChange = {
                                    portText = it
                                    errorMsg = null
                                }
                            )
                        }
                    }
                    // 内联错误行（H5 用 toast，Compose 端无全局 toast；恒占位防布局跳动）
                    Text(
                        text = errorMsg ?: "",
                        fontSize = 11.sp,
                        color = easyAdbColors.StatusUnauthorized,
                        modifier = Modifier.padding(start = 2.dp)
                    )
                }
                // H5 .modal-foot：右对齐 取消/连接，padding 18 22 20
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.End)
                ) {
                    DialogPlainButton(text = "取消", onClick = onDismiss)
                    DialogPrimaryButton(text = "连接", onClick = { tryConnect() })
                }
            }
        }
    }
}

// ───────────────────────── 头部关闭钮（H5 .modal-x） ─────────────────────────

/** 30×30、8dp 圆角，hover 亮底 ModalXHoverBg + 图标转亮；关闭钮与取消等效 */
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

// ───────────────────────── mfield 输入组件 ─────────────────────────

/**
 * H5 .mfield：label（12.5sp/600 TextSecondary）+ 输入框（等宽 13sp、高 40dp、
 * padding 13/10、10dp 圆角、Field 底、Border 描边、focus 蓝描边）。
 * 自绘 BasicTextField（参照 AppParamPanel.CompactTextField 模式，规格不同不提取）。
 * 焦点同步上报 TextInputFocusTracker（浮层在主窗口内，聚焦时键盘须本地优先）。
 */
@Composable
private fun MField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String = ""
) {
    var isFocused by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = easyAdbColors.TextSecondary,
            modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .border(
                    width = 1.dp,
                    color = if (isFocused) easyAdbColors.Primary else easyAdbColors.Border,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(easyAdbColors.Field, RoundedCornerShape(10.dp))
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
                modifier = Modifier.fillMaxWidth()
            )
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    color = easyAdbColors.TextHint
                )
            }
        }
    }
}

// ───────────────────────── 底部按钮（H5 .btn 通用规格） ─────────────────────────

/**
 * 主操作按钮（连接），对齐 H5 .btn + .btn-primary：14px/600 白字、9/20 padding
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

/** .btn-primary 渐变与投影固定色（H5 明暗同值硬编码） */
private val PrimaryGradTop = Color(0xFF3F88F8)
private val PrimaryGradBottom = Color(0xFF2563EB)
private val PrimaryShadow = Color(0x612563EB)
