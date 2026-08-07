package com.easyadb.ui.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.Button
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Slider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.EasyAdbColors
import com.easyadb.ui.designsystem.GroupBox
import kotlin.math.roundToInt

/**
 * 屏幕录制参数。
 *
 * @param timeLimit 录制时长（秒），对应 `--time-limit`
 * @param customResolution 自定义分辨率（如 "1920x1080"），空字符串表示不指定
 * @param bitRate 比特率（字节），对应 `--bit-rate`，null 表示不指定
 * @param rotate 是否旋转输出，对应 `--rotate`
 */
data class ScreenRecordOptions(
    val timeLimit: Int = 90,
    val customResolution: String = "",
    val bitRate: String? = null,
    val rotate: Boolean = false
)

/**
 * 屏幕录制对话框。
 *
 * 对齐 Python `src/widget/ScreenRecord.py::Record_Dialog`：
 * - 标题："视频录制"
 * - 分辨率 / 比特率（水平并排，各占一半）
 * - 时间限制（滑块 5-180 秒）
 * - 旋转选项
 * - 开始 / 终止 / 拉取 按钮
 *
 * 录制状态（isRecording）由外部传入，控制按钮启用/禁用。
 */
@Composable
fun ScreenRecordDialog(
    isRecording: Boolean,
    remainingSeconds: Int,
    onDismiss: () -> Unit,
    onRecordStart: (options: ScreenRecordOptions) -> Unit,
    onRecordStop: () -> Unit,
    onPull: () -> Unit
) {
    var timeLimit by remember { mutableStateOf(90f) }
    var customResEnabled by remember { mutableStateOf(false) }
    var resolution by remember { mutableStateOf("1920x1080") }
    var customBitrateEnabled by remember { mutableStateOf(false) }
    var bitRate by remember { mutableStateOf("8000000") }
    var rotate by remember { mutableStateOf(false) }
    val dialogState = rememberDialogState(width = 300.dp, height = 350.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "视频录制",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // ── 分辨率 + 比特率（水平平分） ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    GroupBox("分辨率", Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp)
                        ) {
                            CompactCheckbox(
                                checked = customResEnabled,
                                onCheckedChange = {
                                    customResEnabled = it
                                    if (!it) resolution = "1920x1080"
                                }
                            )
                            Text("自定义", fontSize = 11.sp)
                        }
                        OutlinedTextField(
                            value = resolution,
                            onValueChange = { resolution = it },
                            enabled = customResEnabled,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(46.dp).defaultMinSize(minHeight = 0.dp),
                            textStyle = TextStyle(fontSize = 11.sp)
                        )
                    }

                    GroupBox("比特率", Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.height(24.dp)
                        ) {
                            CompactCheckbox(
                                checked = customBitrateEnabled,
                                onCheckedChange = {
                                    customBitrateEnabled = it
                                    if (!it) bitRate = "8000000"
                                }
                            )
                            Text("自定义（字节）", fontSize = 11.sp)
                        }
                        OutlinedTextField(
                            value = bitRate,
                            onValueChange = { bitRate = it },
                            enabled = customBitrateEnabled,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().height(46.dp).defaultMinSize(minHeight = 0.dp),
                            textStyle = TextStyle(fontSize = 11.sp)
                        )
                    }
                }

                // ── 时间限制 ──
                GroupBox("时间限制",
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "录制时间(秒)：${timeLimit.roundToInt()}",
                        fontSize = 11.sp,
                        color = EasyAdbColors.TextPrimary
                    )
                    Slider(
                        modifier = Modifier.height(24.dp),
                        value = timeLimit,
                        onValueChange = { timeLimit = it },
                        valueRange = 5f..180f,
                        steps = (180 - 5) / 5 - 1,
                        enabled = !isRecording
                    )
                }

                // ── 旋转 ──
                GroupBox("旋转", Modifier.fillMaxWidth()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.height(24.dp)
                    ) {
                        CompactCheckbox(
                            checked = rotate,
                            onCheckedChange = { rotate = it },
                            enabled = !isRecording
                        )
                        Text("输出视频旋转90度（实验性）", fontSize = 11.sp)
                    }
                }

                // ── 按钮区域 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Button(
                        enabled = !isRecording,
                        onClick = {
                            val options = ScreenRecordOptions(
                                timeLimit = timeLimit.roundToInt(),
                                customResolution = if (customResEnabled) resolution.trim() else "",
                                bitRate = if (customBitrateEnabled) bitRate.trim() else null,
                                rotate = rotate
                            )
                            onRecordStart(options)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isRecording) "${remainingSeconds}s" else "开始")
                    }
                    Button(
                        enabled = isRecording,
                        onClick = onRecordStop,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("终止")
                    }
                    Button(
                        enabled = !isRecording,
                        onClick = onPull,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("拉取")
                    }
                }
            }
        }
    }
}

/**
 * 紧凑复选框：用 Icon 实现，无内置 padding，完全靠左对齐。
 * （Material Checkbox 自带 ~4dp 内边距无法去除，故自定义）
 */
@Composable
private fun CompactCheckbox(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    Box(
        modifier = modifier
            .size(20.dp)
            .clickable(enabled = enabled) { onCheckedChange(!checked) },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.CheckBox else Icons.Default.CheckBoxOutlineBlank,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (enabled) MaterialTheme.colors.primary else EasyAdbColors.TextSecondary
        )
    }
}

