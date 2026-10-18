package com.easyadb.ui.filetransfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.TextInputFocusTracker
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import androidx.compose.ui.focus.onFocusChanged

/**
 * 另存文件到电脑弹窗（SP-5001，对齐 H5 #modalSaveAs）。
 *
 * 保存目录可手输**任意本机路径**（脱离左侧 PC 面板当前目录限制）、文件名可改名；
 * 打开前由调用方预填（目录=PC 树当前目录，文件名=设备文件原名）。
 * DialogWindow 独立窗口式对话框（工程统一模式，NewConnectDialog 同构）。
 */
@Composable
fun SaveAsDialog(
    dir: String,
    name: String,
    onDirChange: (String) -> Unit,
    onNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        // 高度须容纳两个输入组 + 按钮行（与 ConfirmDeleteDialog 同教训：偏小裁切按钮）
        state = rememberDialogState(width = 460.dp, height = 340.dp),
        title = "另存文件到电脑",
        resizable = false
    ) {
        Surface(color = easyAdbColors.Surface) {
            Column(
                modifier = Modifier.padding(easyAdbTokens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceMd)
            ) {
                Text(
                    "文件将通过 adb pull 保存到上述位置——不受左侧 PC 面板当前目录限制。",
                    fontSize = 11.sp,
                    color = easyAdbColors.TextSecondary
                )
                Column(verticalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceSm)) {
                    Text("保存目录", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextSecondary)
                    OutlinedTextField(
                        value = dir,
                        onValueChange = onDirChange,
                        singleLine = true,
                        placeholder = { Text("可填写本机任意目录", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { TextInputFocusTracker.onFocusChanged(it.hasFocus) }
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceSm)) {
                    Text("文件名", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextSecondary)
                    OutlinedTextField(
                        value = name,
                        onValueChange = onNameChange,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                            .onFocusChanged { TextInputFocusTracker.onFocusChanged(it.hasFocus) }
                    )
                }
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Button(
                        onClick = onConfirm,
                        enabled = dir.isNotBlank() && name.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = easyAdbColors.Primary,
                            contentColor = easyAdbColors.TextOnPrimary
                        )
                    ) { Text("开始拉取") }
                }
            }
        }
    }
}
