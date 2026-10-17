package com.easyadb.ui.dialogs

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * 文本输入对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::TextInputDialog`：
 * - 标题："文本输入"
 * - 多行文本输入框
 * - 清空按钮 + 输入按钮
 * - 确认后回调 [onInput]，宿主负责执行 `adb shell input text`（含换行/空格转义）。
 */
@Composable
fun TextInputDialog(
    onDismiss: () -> Unit,
    onInput: (text: String) -> Unit
) {
    var text by remember { mutableStateOf("") }
    val dialogState = rememberDialogState(width = 500.dp, height = 200.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "文本输入",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = easyAdbColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = easyAdbTokens.spaceLg, vertical = easyAdbTokens.spaceMd),
                verticalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceMd)
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("请输入要发送到设备的文本...", fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 13.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Start,
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    // 次级动作：描边按钮（P12 webUI 语言）
                    TextButton(
                        onClick = { text = "" },
                        shape = easyAdbTokens.shapeMd,
                        colors = ButtonDefaults.textButtonColors(contentColor = easyAdbColors.TextPrimary)
                    ) {
                        Text("清空")
                    }
                    Box(modifier = Modifier.weight(1f))
                    // 主确认动作：品牌色实底按钮
                    Button(
                        enabled = text.isNotBlank(),
                        onClick = {
                            onInput(text)
                            text = ""
                        },
                        shape = easyAdbTokens.shapeMd,
                        elevation = ButtonDefaults.elevation(defaultElevation = 0.dp, pressedElevation = 0.dp),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = easyAdbColors.Primary,
                            contentColor = Color.White
                        )
                    ) {
                        Text("输入")
                    }
                }
            }
        }
    }
}
