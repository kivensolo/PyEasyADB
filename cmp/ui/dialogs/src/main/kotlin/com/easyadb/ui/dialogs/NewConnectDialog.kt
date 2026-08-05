package com.easyadb.ui.dialogs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.core.util.NetworkUtils
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 新建连接对话框。
 *
 * 功能：
 * - 输入设备 IP 地址（支持可选端口，默认 5555）
 * - 验证 IP 格式
 * - 确认后回调 [onConnect]，宿主负责执行 adb connect 并刷新设备列表。
 */
@Composable
fun NewConnectDialog(
    onDismiss: () -> Unit,
    onConnect: (deviceIp: String) -> Unit
) {
    var ipText by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val dialogState = rememberDialogState(width = 400.dp, height = 170.dp)

    DialogWindow(
        onCloseRequest = onDismiss,
        state = dialogState,
        title = "新建连接",
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
                    text = "请输入设备 IP 地址：",
                    fontSize = 12.sp,
                    color = EasyAdbColors.TextPrimary
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ipText,
                        onValueChange = {
                            ipText = it
                            errorMsg = null
                        },
                        placeholder = { Text("目标设备IP", fontSize = 12.sp) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        isError = errorMsg != null
                    )
                    Box(modifier = Modifier.width(8.dp))
                    Button(
                        enabled = ipText.isNotBlank(),
                        onClick = {
                            val ip = ipText.trim()
                            if (NetworkUtils.isIpMatches(ip)) {
                                val fullIp = if (!ip.contains(":")) "$ip:5555" else ip
                                onConnect(fullIp)
                            } else {
                                errorMsg = "无效的 IP 地址格式"
                            }
                        }
                    ) {
                        Text("连接")
                    }
                }

                if (errorMsg != null) {
                    Text(
                        text = errorMsg!!,
                        color = MaterialTheme.colors.error,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}
