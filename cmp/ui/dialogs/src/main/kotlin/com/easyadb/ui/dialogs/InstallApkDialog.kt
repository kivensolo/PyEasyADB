package com.easyadb.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
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
import java.awt.FileDialog
import java.awt.Frame
import java.io.FilenameFilter

/**
 * 安装应用选项。
 *
 * @param replace 替换安装（-r），保留数据和签名
 * @param testApp 允许测试包（-t）
 * @param downgrade 降级安装（-d）
 */
data class InstallOptions(
    val replace: Boolean = true,
    val testApp: Boolean = false,
    val downgrade: Boolean = false
)

/**
 * 安装应用对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::installApkDialog`：
 * - 标题："应用安装"
 * - 文件路径输入框 + 浏览按钮（选择 APK）+ 安装按钮
 * - 三个安装模式复选框：替换安装 / Test包 / 降级安装
 * - 确认后回调 [onInstall]，宿主负责执行 `adb install` 命令。
 *
 * 注意：拖拽 APK 功能待后续用 Swing interop（DropTarget）实现，
 * 当前通过「浏览」按钮选择文件。
 */
@Composable
fun InstallApkDialog(
    onDismiss: () -> Unit,
    onInstall: (apkPath: String, options: InstallOptions) -> Unit
) {
    var apkPath by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(InstallOptions()) }
    val dialogState = rememberDialogState(width = 600.dp, height = 200.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "应用安装",
        resizable = false
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── 文件选择区域 ──
                Text(
                    text = "安装应用",
                    fontSize = 13.sp,
                    color = EasyAdbColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = apkPath,
                        onValueChange = { apkPath = it },
                        placeholder = { Text("可将apk文件直接拖入或点击浏览", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(8.dp))
                    Button(onClick = {
                        val dialog = FileDialog(null as Frame?, "选择 APK 文件", FileDialog.LOAD).apply {
                            filenameFilter = FilenameFilter { _, name -> name.endsWith(".apk") }
                        }
                        dialog.isVisible = true
                        val selectedDir = dialog.directory
                        val selectedFile = dialog.file
                        if (selectedDir != null && selectedFile != null) {
                            apkPath = java.io.File(selectedDir, selectedFile).absolutePath
                        }
                    }) {
                        Text("浏览")
                    }
                    Box(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = apkPath.isNotBlank(),
                        onClick = { onInstall(apkPath.trim(), options) }
                    ) {
                        Text("安装")
                    }
                }

                // ── 安装模式选项 ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = options.replace,
                        onCheckedChange = { options = options.copy(replace = it) }
                    )
                    Text("替换安装", fontSize = 12.sp, color = EasyAdbColors.TextPrimary)
                    Box(modifier = Modifier.width(16.dp))
                    Checkbox(
                        checked = options.testApp,
                        onCheckedChange = { options = options.copy(testApp = it) }
                    )
                    Text("Test包", fontSize = 12.sp, color = EasyAdbColors.TextPrimary)
                    Box(modifier = Modifier.width(16.dp))
                    Checkbox(
                        checked = options.downgrade,
                        onCheckedChange = { options = options.copy(downgrade = it) }
                    )
                    Text("降级安装", fontSize = 12.sp, color = EasyAdbColors.TextPrimary)
                }
            }
        }
    }
}
