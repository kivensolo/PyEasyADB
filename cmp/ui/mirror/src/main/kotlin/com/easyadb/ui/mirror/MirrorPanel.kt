package com.easyadb.ui.mirror

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Checkbox
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.adb.MirrorState
import com.easyadb.ui.designsystem.LogColor
import com.easyadb.ui.designsystem.easyAdbColors

/**
 * 设备实时预览面板（P8：Scrcpy 独立窗口模式）。
 *
 * 对齐 Python `win32Wrapper.py::ScrcpyEmbedWidget` 的控制区：
 * - 「开启实时预览」/「关闭实时预览」按钮（状态互斥启用）
 * - 「嵌入模式」checkbox —— MVP 阶段灰掉（窗口嵌入是后续可选增强 P12）
 * - scrcpy 二进制缺失时显示红色下载指引（对齐 Python 的 WARRING 文案）
 *
 * 进程生命周期由宿主持有的 [com.easyadb.core.adb.ScrcpyLauncher] 管理，
 * 本面板只按 [mirrorState] 渲染并回调 [onStart]/[onStop]。
 *
 * @param deviceIp 当前选中设备 IP（null 时「开启」禁用）
 * @param mirrorState 镜像状态（Idle / Running）
 * @param binaryReady scrcpy.exe 是否就绪
 * @param binaryPath scrcpy.exe 期望路径（缺失时展示在指引中）
 */
@Composable
fun MirrorPanel(
    deviceIp: String?,
    mirrorState: MirrorState,
    binaryReady: Boolean,
    binaryPath: String,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isRunning = mirrorState is MirrorState.Running

    Column(
        modifier = modifier.fillMaxSize().padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // ── 控制按钮行（对齐 Python buttonsLayout） ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                enabled = deviceIp != null && !isRunning,
                onClick = onStart
            ) {
                Text("开启实时预览")
            }
            Button(
                enabled = isRunning,
                onClick = onStop
            ) {
                Text("关闭实时预览")
            }
            // 嵌入模式：MVP 灰掉（后续可选增强，见迁移计划 P12）
            Checkbox(checked = false, onCheckedChange = null, enabled = false)
            Text("嵌入模式", fontSize = 10.sp, color = easyAdbColors.TextSecondary)
            Text(
                text = "(嵌入模式若出现键盘无法控制远程设备的情况,请点击一下此处红色文本.)",
                fontSize = 10.sp,
                color = LogColor.Error
            )
        }

        // ── 状态行 ──
        Text(
            text = when {
                isRunning ->
                    "镜像运行中：${(mirrorState as MirrorState.Running).deviceIp}（独立 scrcpy 窗口「EasyADB Device Mirror」）"
                deviceIp == null -> "未选中设备——请先在左侧设备列表中选择设备"
                else -> "当前设备：$deviceIp"
            },
            fontSize = 12.sp,
            color = easyAdbColors.TextPrimary
        )

        // ── scrcpy 缺失指引（对齐 Python WARRING 文案，手动安装路径） ──
        if (!binaryReady) {
            Text(
                text = buildString {
                    appendLine("系统找不到 scrcpy。请从 https://github.com/Genymobile/scrcpy/releases 下载编译好的 win64 版本，")
                    appendLine("例如 scrcpy-win64-v3.3.4.zip，解压后将文件夹改名为 scrcpy-win64，放入以下任一位置：")
                    appendLine("  · $binaryPath（推荐，程序数据目录）")
                    appendLine("  · 程序运行目录下的 tool/scrcpy-win64")
                    appendLine("后续升级 scrcpy 版本只需替换 scrcpy-win64 目录下的文件，无缝升级。")
                },
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = LogColor.Error
            )
        }

        // ── 操作说明（对齐 Python） ──
        Text(
            text = "【说明】scrcpy 的操作是右键点击返回。",
            fontSize = 11.sp,
            color = easyAdbColors.TextSecondary
        )
    }
}
