package com.easyadb.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
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

/**
 * 添加新应用对话框。
 *
 * 对齐 Python `src/widget/Dialogs.py::AddPackageDialog`：
 * - 标题："添加新应用"
 * - 输入框：占位符 "Input package name"
 * - 按钮："Add"
 * - 确认后回调 [onAdd]，宿主负责写入数据库并刷新下拉列表。
 */
@Composable
fun AddPackageDialog(
    onDismiss: () -> Unit,
    onAdd: (packageName: String) -> Unit
) {
    var pkgText by remember { mutableStateOf("") }
    val dialogState = rememberDialogState(width = 400.dp, height = 170.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "添加新应用",
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
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "请输入应用包名：",
                    fontSize = 12.sp,
                    color = EasyAdbColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = pkgText,
                        onValueChange = { pkgText = it },
                        placeholder = { Text("Input package name", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Box(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = pkgText.isNotBlank(),
                        onClick = { onAdd(pkgText.trim()) }
                    ) {
                        Text("Add")
                    }
                }
            }
        }
    }
}
