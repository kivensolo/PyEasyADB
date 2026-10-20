@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.filetransfer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
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
    // 树状态作用域 = 面板组合协程（rememberCoroutineScope 的 Compose UI 调度器；
    // Desktop JVM 无 Dispatchers.Main 服务提供者，禁自建 Main 作用域）。
    // PC 根 = 「此电脑」虚拟根（空路径，子级为盘符）；未选中时传输落点兜底用户主目录
    val pcState = remember {
        FileTreeState(
            rootPath = PcFileLister.THIS_PC_ROOT,
            scope = scope,
            defaultDir = PcFileLister.DEFAULT_SAVE_DIR.absolutePath
        ) { PcFileLister.list(it) }
    }
    // 设备切换：整树重建（remember(deviceIp)），展开/选中随旧实例丢弃；
    // 根 = 整机根目录（可进入 /data /system 等），传输落点兜底 /sdcard（根只读）
    val devState = remember(deviceIp) {
        deviceIp?.let { ip ->
            FileTreeState(
                rootPath = DeviceFileLister.DEVICE_ROOT,
                scope = scope,
                defaultDir = DeviceFileLister.DEFAULT_TRANSFER_DIR
            ) { DeviceFileLister.list(ip, it) }
        }
    }

    // 另存弹窗 / 删除确认弹窗状态
    var saveAsFile by remember { mutableStateOf<FileEntry?>(null) }
    var saveDir by remember { mutableStateOf("") }
    var saveName by remember { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<FileEntry?>(null) }

    /** 面板级 adb 命令（mkdir/rm）：回显命令 → IO 执行 → 结果回调（传输走 TransferCenter）；
     *  成功判定校验退出码（ExecResult.success 只表示进程结束，adb 失败也返回"成功"） */
    fun runAdb(cmd: String, timeoutMs: Long = 20_000, onDone: (success: Boolean, output: String) -> Unit) {
        onLog("> $cmd", 2)
        scope.launch {
            val result = ExecUtils.execCmd(cmd, timeoutMs = timeoutMs, shell = true)
            onDone(result.success && result.exitCode == 0, result.output)
        }
    }

    fun warn(msg: String) = onLog("[提示] $msg", 3)

    /** PC 侧「在资源管理器打开」：目录直接打开，文件定位（explorer /select） */
    fun openInExplorer(entry: FileEntry) {
        runCatching {
            val process = if (entry.isDir) {
                ProcessBuilder("explorer.exe", entry.path)
            } else {
                ProcessBuilder("explorer.exe", "/select,${entry.path}")
            }
            process.start()
        }.onFailure { onLog("[提示] 打开资源管理器失败：${it.message}", 3) }
    }

    /** 复制设备路径到系统剪贴板（AWT Toolkit），控制台回显结果 */
    fun copyDevicePath(entry: FileEntry) {
        runCatching {
            java.awt.Toolkit.getDefaultToolkit().systemClipboard
                .setContents(java.awt.datatransfer.StringSelection(entry.path), null)
        }.onSuccess { onLog("[复制路径] ${entry.path}", 2) }
            .onFailure { onLog("[提示] 复制路径失败：${it.message}", 3) }
    }

    /**
     * 设备树右键菜单内容（工具栏功能全集，走查补强）：
     * 目录项 → 新建(二级：文件夹/文件) ｜ 投递到左侧/另存为/提取(推送)/删除 ｜ 刷新/复制路径；
     * 文件项无新建组。分组间由 [MenuItemSpec.Separator] 横线区分。
     * 「提取」对位工具栏推送按钮：选本机文件推入该目录（目录项）或其父目录（文件项）。
     */
    fun deviceContextItems(state: FileTreeState, entry: FileEntry): List<MenuItemSpec> {
        // 提取（推送）落点：目录→其内；文件→父目录。落点为整机根（只读）时禁用
        val pushTarget = if (entry.isDir) entry.path else FileTreeState.parentOf(entry.path)
        val pushBlocked = pushTarget == DeviceFileLister.DEVICE_ROOT
        val items = mutableListOf<MenuItemSpec>()
        if (entry.isDir) {
            items += MenuItemSpec.Submenu(
                MenuAction("新建", EasyAdbTileIcons.FolderPlus) {},
                listOf(
                    SubMenuAction("文件", EasyAdbTileIcons.FileDoc) {
                        state.ensureExpandedChain(entry.path)
                        state.beginCreate(entry.path, isDir = false,
                            siblingNames = state.childrenOf(entry.path)?.map { it.name } ?: emptyList())
                    },
                    SubMenuAction("文件夹", EasyAdbTileIcons.Folder) {
                        state.ensureExpandedChain(entry.path)
                        state.beginCreate(entry.path, isDir = true,
                            siblingNames = state.childrenOf(entry.path)?.map { it.name } ?: emptyList())
                    }
                )
            )
            items += MenuItemSpec.Separator
        }
        items += MenuItemSpec.Action(MenuAction("投递到左侧", EasyAdbTileIcons.DeliverLeft) {
            transferCenter.submitPull(deviceIp.orEmpty(), entry.path, pcState.currentDir())
        })
        items += MenuItemSpec.Action(MenuAction("另存为…", EasyAdbTileIcons.SaveAsFile) {
            saveAsFile = entry
            saveDir = pcState.currentDir().replace('/', '\\')
            saveName = entry.name
        })
        items += MenuItemSpec.Action(
            MenuAction("提取…", EasyAdbTileIcons.PushFile, enabled = !pushBlocked) {
                val dialog = FileDialog(null as Frame?, "推送文件到设备", FileDialog.LOAD)
                dialog.isVisible = true
                val local = dialog.file?.let { File(dialog.directory, it).absolutePath }
                if (local != null) {
                    transferCenter.submitPush(deviceIp.orEmpty(), local, pushTarget)
                }
            }
        )
        items += MenuItemSpec.Action(MenuAction("删除", EasyAdbTileIcons.UninstallApp, danger = true) {
            // 受保护目录守卫：整机根/存储入口/系统分区误删导致设备异常
            if (entry.isDir && DeviceFileLister.isProtectedDir(entry.path)) {
                onLog("[提示] 受保护目录 ${entry.path} 不允许删除（防设备异常）", 3)
            } else {
                deleteTarget = entry
            }
        })
        items += MenuItemSpec.Separator
        items += MenuItemSpec.Action(MenuAction("刷新", AppIcons.Refresh) {
            state.reloadPath(if (entry.isDir) entry.path else FileTreeState.parentOf(entry.path))
        })
        items += MenuItemSpec.Action(MenuAction("复制路径", EasyAdbTileIcons.CopyPath) {
            copyDevicePath(entry)
        })
        return items
    }

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
                    },
                    // PC 树右键菜单（「在资源管理器打开」/「刷新当前目录」——目录刷自身、文件刷所在目录）
                    contextMenu = { entry, close ->
                        Surface(
                            shape = easyAdbTokens.shapeLg,
                            elevation = easyAdbTokens.elevationMid,
                            color = easyAdbColors.Surface
                        ) {
                            Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                                ContextMenuItem("在资源管理器打开") {
                                    close()
                                    openInExplorer(entry)
                                }
                                ContextMenuItem("刷新当前目录") {
                                    close()
                                    pcState.reloadPath(if (entry.isDir) entry.path else FileTreeState.parentOf(entry.path))
                                }
                            }
                        }
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
                        // 设备树右键菜单：工具栏功能全集（新建二级/投递/另存/提取/删除/刷新/复制路径）
                        contextMenu = { entry, close ->
                            ContextActionsMenu(
                                items = deviceContextItems(state, entry),
                                onDismiss = close
                            )
                        },
                        toolbar = {
                            TreeToolButton(
                                icon = EasyAdbTileIcons.DeliverLeft,
                                tip = "投递选中项到左侧当前目录（adb pull，目录递归）",
                                enabled = state.selectedEntry != null
                            ) {
                                val target = state.selectedEntry ?: return@TreeToolButton
                                transferCenter.submitPull(deviceIp.orEmpty(), target.path, pcState.currentDir())
                            }
                            // 写操作落点 = 选中目录 / 选中文件父目录（currentDir 口径）；
                            // 落点为整机根（只读）时禁用新建与推送——**选中根下文件同样命中**
                            // （currentDir 返回其父目录 `/`，判据必须按落点而非选中项本身）
                            val targetIsDeviceRoot = state.currentDir() == DeviceFileLister.DEVICE_ROOT
                            TreeToolButton(
                                icon = EasyAdbTileIcons.FolderPlus,
                                tip = if (targetIsDeviceRoot) "设备根目录不可新建文件夹（请选中具体目录）" else "新建文件夹（就地编辑名称，回车确认；在选中目录内创建）",
                                enabled = !targetIsDeviceRoot
                            ) {
                                // 父目录 = 选中目录 / 选中文件父目录 / 默认传输目录；
                                // 展开祖先链使父目录可见后进入名称编辑态（预填名避开同级重名）
                                val parentPath = state.currentDir()
                                state.ensureExpandedChain(parentPath)
                                state.beginCreate(parentPath, isDir = true, siblingNames = state.childrenOf(parentPath)?.map { it.name } ?: emptyList())
                            }
                            TreeToolButton(
                                icon = EasyAdbTileIcons.SaveAsFile,
                                tip = "另存到电脑…（任意本机目录 + 改名；目录递归拉取）",
                                enabled = state.selectedEntry != null
                            ) {
                                val target = state.selectedEntry ?: return@TreeToolButton
                                saveAsFile = target
                                saveDir = pcState.currentDir().replace('/', '\\')
                                saveName = target.name
                            }
                            TreeToolButton(
                                icon = EasyAdbTileIcons.PushFile,
                                tip = if (targetIsDeviceRoot) "设备根目录不可写入（请选中具体目录）" else "推送文件到设备…（选择本机任意文件，推到选中目录）",
                                enabled = !targetIsDeviceRoot
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
                                tip = "删除选中文件 / 目录（确认后执行 adb shell rm）",
                                enabled = state.selectedEntry != null,
                                danger = true
                            ) {
                                val target = state.selectedEntry ?: return@TreeToolButton
                                // 受保护目录守卫：整机根/存储入口/系统分区误删导致设备异常
                                if (target.isDir && DeviceFileLister.isProtectedDir(target.path)) {
                                    onLog("[提示] 受保护目录 ${target.path} 不允许删除（防设备异常）", 3)
                                } else {
                                    deleteTarget = target
                                }
                            }
                            // 刷新紧随删除（走查反馈：刷新原在行尾与删除隔太远）
                            TreeToolButton(icon = AppIcons.Refresh, tip = "刷新设备文件树（ls -la）") { state.refresh() }
                            Spacer(Modifier.weight(1f))
                            TreeHint()
                        },
                        // 就地新建提交：文件夹 mkdir / 文件 touch（重名由 adb 失败回显）
                        onCreateCommit = { parentPath, name ->
                            val isDir = state.creatingIsDir
                            val newPath = "$parentPath/$name"
                            val cmd = if (isDir) {
                                DeviceFileLister.mkdirCmd(deviceIp.orEmpty(), newPath)
                            } else {
                                DeviceFileLister.touchCmd(deviceIp.orEmpty(), newPath)
                            }
                            runAdb(cmd) { ok, output ->
                                if (ok) {
                                    val node = FileEntry(path = newPath, name = name, isDir = isDir)
                                    state.insertEntry(parentPath, node)
                                    state.selectedEntry = node
                                    onLog(if (isDir) "[新建成功] ${newPath}" else "[新建文件成功] ${newPath}", 2)
                                } else {
                                    onLog("[新建失败] ${output.take(160)}", 4)
                                }
                            }
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
                // 目录存在性校验：不存在则提示并保持弹窗打开（adb pull 无法落盘）
                val dir = saveDir.trim()
                if (dir.isBlank() || saveName.isBlank()) {
                    onLog("[提示] 请填写保存目录和文件名", 3)
                } else if (!File(dir).isDirectory) {
                    onLog("[提示] 保存目录不存在：$dir", 3)
                } else {
                    transferCenter.submitPull(deviceIp.orEmpty(), saveAsFile!!.path, dir, saveName.trim())
                    saveAsFile = null
                }
            },
            onDismiss = { saveAsFile = null }
        )
    }

    // 删除确认弹窗：文件 rm -f / 目录 rm -rf（递归），结果回显控制台 + 成功行移除
    deleteTarget?.let { target ->
        ConfirmDeleteDialog(
            entry = target,
            onDismiss = { deleteTarget = null },
            onConfirm = {
                deleteTarget = null
                val cmd = if (target.isDir) {
                    DeviceFileLister.rmDirCmd(deviceIp.orEmpty(), target.path)
                } else {
                    DeviceFileLister.rmCmd(deviceIp.orEmpty(), target.path)
                }
                runAdb(cmd) { ok, output ->
                    if (ok) {
                        devState?.removeEntry(FileTreeState.parentOf(target.path), target)
                        onLog("[删除成功] ${target.path}", 2)
                    } else {
                        onLog("[删除失败] ${target.path}：${output.take(200)}", 4)
                    }
                }
            }
        )
    }
}

/** 树右键菜单项：等宽列内自适应，hover 淡底、无涟漪点击 */
@Composable
private fun ContextMenuItem(label: String, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (hovered) easyAdbColors.Hover else androidx.compose.ui.graphics.Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(interactionSource = null, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, fontSize = 12.sp, color = easyAdbColors.TextPrimary)
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
