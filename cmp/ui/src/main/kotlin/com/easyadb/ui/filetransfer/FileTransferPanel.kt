package com.easyadb.ui.filetransfer

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.file.DeviceFileLister
import com.easyadb.core.file.FileEntry
import com.easyadb.core.file.PcFileLister
import com.easyadb.core.file.TransferCenter
import com.easyadb.core.util.ExecUtils
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.fileDropTarget
import com.easyadb.ui.home.components.SplitPane
import kotlinx.coroutines.launch
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * 文件传输 Tab 面板（SP-5001）——双栏 Device Explorer + 传输队列。
 *
 * 左 PC 本地树 / 右设备树（SplitPane 拖拽分栏），行选中 + 目录懒加载展开；
 * 传输统一走工具栏：投递（本侧选中文件 → 对侧当前目录）、另存弹窗（任意
 * 目录+改名）、FileDialog 推送、设备面板拖放接收。传输执行与队列状态由
 * 宿主注入的 [TransferCenter] 承担（切 Tab 面板销毁传输不中断）。
 *
 * @param deviceIp 当前选中设备（null = 未选择，右栏空态）
 * @param onLog 控制台回显（message + level：2=info / 3=warn / 4=error）
 */
@Composable
fun FileTransferPanel(
    deviceIp: String?,
    transferCenter: TransferCenter,
    onLog: (String, Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val pcState = remember {
        FileTreeState(PcFileLister.PC_ROOT.absolutePath) { PcFileLister.list(File(it)) }
    }
    // 设备切换：整树重建（remember(deviceIp)），展开/选中随旧实例丢弃
    val devState = remember(deviceIp) {
        deviceIp?.let { ip ->
            FileTreeState(DeviceFileLister.DEVICE_ROOT) { DeviceFileLister.list(ip, it) }
        }
    }

    // 另存弹窗状态
    var saveAsFile by remember { mutableStateOf<FileEntry?>(null) }
    var saveDir by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }

    /** 面板级 adb 命令（mkdir/rm）：回显命令 → IO 执行 → 结果回调（传输走 TransferCenter） */
    fun runAdb(cmd: String, timeoutMs: Long = 20_000, onDone: (Boolean) -> Unit) {
        onLog("> $cmd", 2)
        scope.launch {
            val result = ExecUtils.execCmd(cmd, timeoutMs = timeoutMs, shell = true)
            onDone(result.success)
        }
    }

    fun warn(msg: String) = onLog("[提示] $msg", 3)

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        SplitPane(
            modifier = Modifier.fillMaxWidth().weight(1f),
            initialFraction = 0.5f,
            leftPanel = { m ->
                FileTreePane(
                    state = pcState,
                    pc = true,
                    title = "此电脑",
                    osIconTint = easyAdbColors.PrimaryLight,
                    modifier = m,
                    toolbar = {
                        TreeToolButton(
                            icon = EasyAdbTileIcons.DeliverRight,
                            tip = "投递选中文件到右侧当前目录（adb push）",
                            enabled = pcState.selectedEntry?.isDir == false && deviceIp != null
                        ) {
                            val file = pcState.selectedEntry ?: return@TreeToolButton
                            transferCenter.submitPush(
                                deviceIp.orEmpty(),
                                file.path,
                                devState?.currentDir() ?: DeviceFileLister.DEVICE_ROOT
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TreeHint()
                        Spacer(Modifier.width(8.dp))
                        TreeToolButton(icon = AppIcons.Refresh, tip = "刷新 PC 目录树") { pcState.refresh() }
                    }
                )
            },
            rightPanel = { m ->
                if (devState == null) {
                    Surface(
                        shape = easyAdbTokens.shapeLg,
                        color = easyAdbColors.Surface,
                        elevation = 0.dp,
                        border = androidx.compose.foundation.BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
                        modifier = m.fillMaxSize()
                    ) {
                        EmptyState(
                            icon = AppIcons.Android,
                            title = "未选择设备",
                            subtitle = "请先在左侧设备列表中选择一个设备"
                        )
                    }
                } else {
                    val state = devState
                    FileTreePane(
                        state = state,
                        pc = false,
                        title = "设备存储",
                        osIconTint = easyAdbColors.StatusOnline,
                        modifier = m,
                        // 拖放接收：拖入任意本机文件 → 推送到设备当前选中目录
                        dropModifier = Modifier.fileDropTarget { paths ->
                            val local = paths.firstOrNull() ?: return@fileDropTarget
                            if (deviceIp == null) {
                                warn("未选择设备，无法推送")
                                return@fileDropTarget
                            }
                            transferCenter.submitPush(deviceIp, local, state.currentDir())
                        },
                        toolbar = {
                            TreeToolButton(
                                icon = EasyAdbTileIcons.DeliverLeft,
                                tip = "投递选中文件到左侧当前目录（adb pull）",
                                enabled = state.selectedEntry?.isDir == false
                            ) {
                                val file = state.selectedEntry ?: return@TreeToolButton
                                transferCenter.submitPull(deviceIp.orEmpty(), file.path, pcState.currentDir())
                            }
                            TreeToolButton(
                                icon = EasyAdbTileIcons.FolderPlus,
                                tip = "新建文件夹（adb shell mkdir）"
                            ) {
                                val parent = state.selectedEntry?.takeIf { it.isDir }
                                val parentPath = parent?.path ?: DeviceFileLister.DEVICE_ROOT
                                val siblings = state.childrenOf(parentPath)?.map { it.name } ?: emptyList()
                                var name = "新建文件夹"
                                var n = 2
                                while (name in siblings) { name = "新建文件夹 $n"; n++ }
                                val newPath = "$parentPath/$name"
                                runAdb(DeviceFileLister.mkdirCmd(deviceIp.orEmpty(), newPath)) { ok ->
                                    if (ok) {
                                        val node = FileEntry(path = newPath, name = name, isDir = true)
                                        state.insertEntry(parentPath, node)
                                        state.selectedEntry = node
                                    } else warn("新建文件夹失败")
                                }
                            }
                            TreeToolButton(
                                icon = EasyAdbTileIcons.SaveAsFile,
                                tip = "另存文件到电脑…（任意本机目录 + 改名）",
                                enabled = state.selectedEntry?.isDir == false
                            ) {
                                val file = state.selectedEntry ?: return@TreeToolButton
                                saveAsFile = file
                                saveDir = pcState.currentDir().replace('/', '\\')
                                saveName = file.name
                            }
                            TreeToolButton(
                                icon = EasyAdbTileIcons.PushFile,
                                tip = "推送文件到设备…（选择本机任意文件）"
                            ) {
                                val dialog = FileDialog(null as Frame?, "推送文件到设备", FileDialog.LOAD)
                                dialog.isVisible = true
                                val local = dialog.file?.let { File(dialog.directory, it).absolutePath }
                                if (local != null) {
                                    transferCenter.submitPush(deviceIp.orEmpty(), local, state.currentDir())
                                }
                            }
                            TreeToolButton(
                                icon = EasyAdbTileIcons.UninstallApp,
                                tip = "删除选中文件（adb shell rm）",
                                enabled = state.selectedEntry?.isDir == false,
                                danger = true
                            ) {
                                val file = state.selectedEntry ?: return@TreeToolButton
                                runAdb(DeviceFileLister.rmCmd(deviceIp.orEmpty(), file.path)) { ok ->
                                    if (ok) state.removeEntry(FileTreeState.parentOf(file.path), file)
                                    else warn("删除失败")
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TreeHint()
                            Spacer(Modifier.width(8.dp))
                            TreeToolButton(icon = AppIcons.Refresh, tip = "刷新设备文件树（ls -la）") { state.refresh() }
                        }
                    )
                }
            }
        )
        TransferQueue(transferCenter)
    }

    // 另存弹窗
    if (saveAsFile != null) {
        SaveAsDialog(
            dir = saveDir,
            name = saveName,
            onDirChange = { saveDir = it },
            onNameChange = { saveName = it },
            onConfirm = {
                transferCenter.submitPull(deviceIp.orEmpty(), saveAsFile!!.path, saveDir.trim(), saveName.trim())
                saveAsFile = null
            },
            onDismiss = { saveAsFile = null }
        )
    }
}

/** 工具栏行尾操作提示（H5 .dx-hint） */
@Composable
private fun TreeHint() {
    Text(
        "点击行选中 · 目录行展开 / 收起",
        fontSize = 10.5.sp,
        color = easyAdbColors.TextHint
    )
}
