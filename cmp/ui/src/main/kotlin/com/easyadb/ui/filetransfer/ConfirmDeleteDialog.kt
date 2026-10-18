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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.file.FileEntry
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/** 删除确认按钮底色（H5 .btn-danger #dc2626，明暗同值） */
private val DangerRed = Color(0xFFDC2626)

/**
 * 删除确认弹窗（SP-5001 走查补充）：展示删除目标完整路径——目录为递归删除
 * （`rm -rf`，强调不可撤销），文件为单文件删除。DialogWindow 工程统一模式。
 */
@Composable
fun ConfirmDeleteDialog(
    entry: FileEntry,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    DialogWindow(
        onCloseRequest = onDismiss,
        // 高度须容纳全部内容（走查反馈 220dp 下按钮行被裁出可视区无法点击）
        state = rememberDialogState(width = 460.dp, height = 320.dp),
        title = "删除确认",
        resizable = false
    ) {
        Surface(color = easyAdbColors.Surface) {
            Column(
                modifier = Modifier.padding(easyAdbTokens.spaceLg),
                verticalArrangement = Arrangement.spacedBy(easyAdbTokens.spaceMd)
            ) {
                Text(
                    if (entry.isDir) "确定要删除该目录及其全部内容吗？" else "确定要删除该文件吗？",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = easyAdbColors.TextPrimary
                )
                OutlinedTextField(
                    value = entry.path,
                    onValueChange = {},
                    readOnly = true,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    if (entry.isDir)
                        "目录将被递归删除（adb shell rm -rf），完成后无法撤销；系统目录删除可能导致设备异常，请确认路径无误。"
                    else "文件删除后无法撤销。",
                    fontSize = 11.sp,
                    color = if (entry.isDir) easyAdbColors.LogError else easyAdbColors.TextSecondary
                )
                Spacer(Modifier.height(2.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("取消") }
                    Spacer(Modifier.padding(horizontal = 2.dp))
                    Button(
                        onClick = onConfirm,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = DangerRed,
                            contentColor = Color.White
                        )
                    ) { Text(if (entry.isDir) "递归删除" else "删除") }
                }
            }
        }
    }
}
