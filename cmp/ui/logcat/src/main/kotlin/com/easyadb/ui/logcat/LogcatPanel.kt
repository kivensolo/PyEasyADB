@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.logcat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.ContextMenuState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed as pointerIsCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed as pointerIsShiftPressed
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.easyadb.core.adb.LogcatEntry
import com.easyadb.core.adb.LogcatStream
import com.easyadb.core.log.LogLevel
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.logColor
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 实时日志面板 —— 显示设备 logcat 输出，支持过滤和控制。
 *
 * 对应 Python `BottomWindow.py` 中的 logcat 标签 + `LiveLogAdbThread`。
 *
 * ## 设计要点
 * - **数据源**：直接使用 [LogcatStream.logcatFlow] 获取设备实时 logcat 流（读取循环在
 *   `Dispatchers.IO` 上，不阻塞 UI）；条目先攒进缓冲，每 ~150ms 批量刷入 `logEntries`
 *   （SnapshotStateList，增量 add/remove），避免 logcat 启动倾泻历史缓冲时逐行触发重组。
 * - **过滤**：条件即面板状态（`selectedLevel` / `searchText`），显示列表 = 全量缓冲按条件
 *   派生的子序列——条件变化全量重滤（entry 已带解析好的 level/pid，重滤零 regex，毫秒级），
 *   flush 增量追加通过条件的新行；被过滤的行仍在缓冲中，切回条件即恢复。
 *   PID 过滤（仅看选中应用，对应 Python `onlyShowSelectedApp`）已立项后续。
 * - **着色**：使用 [logColor]（主题化日志级别色）对 [LogcatEntry.level] 着色，字体为 `Monospace`。
 * - **控制**：启动/停止按钮控制 `logcatJob` 的协程生命周期；停止时调用 `logcatStream.stop()`
 *   销毁底层 ADB 进程。
 * - **容量**：按内存预算而非条数管理（[LogcatBuffer]，默认 8MB ≈ 1.5~2 万行）——
 *   LazyColumn 虚拟化下渲染只与可见行数相关，总量唯一约束是内存；超预算从最旧淘汰。
 *   预算是 [LogcatStream] 构造参数，后期可由设置功能注入用户值。
 * - **依赖**：需要 `:core:adb` 模块的 [LogcatStream] 和 [LogcatEntry] 支持。
 */
@Composable
fun LogcatPanel(
    deviceIp: String?,
    logcatStream: LogcatStream,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    var logcatJob by remember { mutableStateOf<Job?>(null) }
    var isRunning by remember { mutableStateOf(false) }
    // SnapshotStateList：flush 只做增量 add/remove，LazyColumn 按结构变化局部更新，
    // 不随缓冲总量（预算制下可达数万条）整表重建
    val logEntries = remember { mutableStateListOf<LogcatEntry>() }
    val listState = rememberLazyListState()
    var autoScroll by remember { mutableStateOf(true) }
    // 累计接收行数：缓冲满后进入稳态（淘汰数==新增数）时列表 size 不再变化，
    // 自动滚动跟随需要靠它驱动
    var receivedCount by remember { mutableStateOf(0) }
    // 日志字号：Ctrl+滚轮在 9~20sp 间缩放，每档 1sp（会话级，不持久化；上下限兼顾可读性与排版）
    var logFontSize by remember { mutableStateOf(12.sp) }
    // ── Find 工具条（Ctrl+F）：匹配以 seq 锚定——预算淘汰会让列表位置持续漂移 ──
    var findBarVisible by remember { mutableStateOf(false) }
    var findQuery by remember { mutableStateOf("") }
    var matchSeqs by remember { mutableStateOf<List<Long>>(emptyList()) }
    var currentMatchSeq by remember { mutableStateOf<Long?>(null) }
    val findInputFocus = remember { FocusRequester() }
    // 右键快捷菜单：记录右键按下位置（相对日志区左上角），DropdownMenu 以该位置为锚点弹出
    // （对齐设备树 TreeItem 的同款右键模式）
    var contextMenuVisible by remember { mutableStateOf(false) }
    var contextMenuOffset by remember { mutableStateOf(IntOffset.Zero) }
    val clipboard = LocalClipboardManager.current
    // 滚动条 hover 状态：进入滚动条区域时给一条淡淡的轨道底色，突出可交互区域
    var scrollbarHovered by remember { mutableStateOf(false) }
    // ── 选区代际：防 SelectionContainer 悬空选区崩溃 ──
    // Compose 1.7 缺陷：LazyColumn 虚拟化把选中行滚出组合后，选区对象仍保留其
    // selectableId；shift 扩展选区时 SelectionManager 的 compareBy 按 id 查
    // MutableLongIntMap，缺 key 直接抛 NoSuchElementException（Cannot find value
    // for key N）。对策：视口版本号（firstVisibleItemIndex 每变一次 +1）与"上次
    // 列表按下时的版本"不一致，说明旧选区可能含已滚出组合的行——下一次列表主键
    // 按下时换代重建 SelectionContainer 并吞掉该次按下（再按一次正常选择）。
    // 换代推迟到按下而非滚动瞬间，避免重建打断进行中的滚动条拖拽/选区拖拽。
    var selectionGeneration by remember { mutableStateOf(0) }
    var viewportVersion by remember { mutableStateOf(0) }
    var pressViewportVersion by remember { mutableStateOf(-1) }
    LaunchedEffect(Unit) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { viewportVersion++ }
    }
    // ── 行区间选择（方案 A）：以 seq 为锚的整行范围选择，不受 LazyColumn 虚拟化限制 ──
    // 开启后点击行设锚点、Shift+点击行扩展端点，两行之间（当前过滤视图内）整行高亮；
    // Ctrl+C / 右键「复制选中行」复制区间全文；Esc 清除。主键按下在预览阶段被消费，
    // 字级选区手势被阻断（虚拟化列表的字级选择本就无法跨视口，见选区代际注释）
    var lineSelectMode by remember { mutableStateOf(false) }
    var lineSelectAnchor by remember { mutableStateOf<Long?>(null) }
    var lineSelectEnd by remember { mutableStateOf<Long?>(null) }

    fun lineSelectRange(): LongRange? {
        val a = lineSelectAnchor ?: return null
        val b = lineSelectEnd ?: return null
        return minOf(a, b)..maxOf(a, b)
    }

    fun copyLineSelection() {
        val range = lineSelectRange() ?: return
        clipboard.setText(
            AnnotatedString(logEntries.filter { it.seq in range }.joinToString("\n") { it.raw })
        )
    }

    // 过滤状态
    var selectedLevel by remember { mutableStateOf(LogLevel.DEBUG) }
    var levelDropdownExpanded by remember { mutableStateOf(false) }
    var searchText by remember { mutableStateOf("") }

    val levelOptions = listOf(
        LogLevel.TRACE, LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR
    )

    // 自动滚动：日志高频到达时逐条动画会排队卡顿，直接跳到底部；查找激活时让位，
    // 不把用户正在查看的匹配位置拽回底部
    LaunchedEffect(receivedCount) {
        val findActive = findBarVisible && findQuery.isNotEmpty()
        if (autoScroll && !findActive && logEntries.isNotEmpty()) {
            listState.scrollToItem(logEntries.size - 1)
        }
    }

    // 过滤条件（级别/关键字）变化 → 全量重滤重建显示视图。entry 已带解析好的 level/pid，
    // 重滤只有数值比较和 contains，2 万条毫秒级；重滤后恢复滚动位置与 Find 索引。
    // 被过滤的行仍在全量缓冲里，切回条件即恢复——这是「过滤视图」与前置丢弃的本质区别
    LaunchedEffect(selectedLevel, searchText) {
        val derived = logcatStream.buffer.snapshot()
            .filter { accepts(it, selectedLevel, searchText) }
        logEntries.clear()
        logEntries.addAll(derived)
        if (findQuery.isNotEmpty()) {
            matchSeqs = derived.filter { it.raw.contains(findQuery, ignoreCase = true) }.map { it.seq }
            val first = matchSeqs.firstOrNull()
            if (first != null && (currentMatchSeq == null || currentMatchSeq !in matchSeqs)) {
                currentMatchSeq = first
            }
        }
        if (autoScroll && logEntries.isNotEmpty()) {
            listState.scrollToItem(logEntries.size - 1)
        }
    }

    // 查询词变化 → 去抖 200ms 后全量扫描（2 万条 contains 仅几毫秒，主线程同步扫即可，
    // 与 flush 协程同在 Main 上顺序执行，避免并发读写列表）；有匹配且当前已失效时自动选中首个
    LaunchedEffect(findQuery) {
        delay(200)
        if (findQuery.isEmpty()) {
            matchSeqs = emptyList()
            currentMatchSeq = null
        } else {
            matchSeqs = logEntries.filter { it.raw.contains(findQuery, ignoreCase = true) }
                .map { it.seq }
            val first = matchSeqs.firstOrNull()
            if (first != null && (currentMatchSeq == null || currentMatchSeq !in matchSeqs)) {
                currentMatchSeq = first
                val pos = positionOf(logEntries, first)
                if (pos >= 0) listState.scrollToItem(pos)
            }
        }
    }

    // FindBar 首次打开：等输入框组合完成后再聚焦（未初始化的 FocusRequester 直接 request 会崩溃）
    LaunchedEffect(findBarVisible) {
        if (findBarVisible) runCatching { findInputFocus.requestFocus() }
    }

    // 启动/停止 logcat
    fun startLogcat() {
        if (deviceIp == null) return
        logcatJob = scope.launch {
            isRunning = true
            // 批量缓冲：logcat 启动即倾泻整个历史缓冲（数千行），逐行写状态会触发
            // 重组风暴；攒批每 ~150ms 刷一次（读取循环在 LogcatStream 内已固定 IO 线程）
            val pending = mutableListOf<LogcatEntry>()

            // 这批条目写入预算缓冲，并让显示列表与缓冲内容增量同步（不整表重建）
            fun flush() {
                if (pending.isEmpty()) return
                logcatStream.buffer.append(pending)
                receivedCount += pending.size
                // 显示视图是全量缓冲的过滤子序列：头部淘汰按 seq 阈值同步（不是按条数——
                // 视图行数 ≠ 缓冲行数，被过滤掉的行不占视图槽位）
                val oldest = logcatStream.buffer.oldestSeq() ?: Long.MIN_VALUE
                if (logEntries.isNotEmpty() && logEntries.first().seq < oldest) {
                    logEntries.subList(0, logEntries.indexOfFirst { it.seq >= oldest }).clear()
                }
                // 尾部追加：仅追加通过当前过滤条件、且未被同批预算淘汰的条目
                logEntries.addAll(pending.filter { it.seq >= oldest && accepts(it, selectedLevel, searchText) })
                // Find 增量维护：只扫新增批；被淘汰的匹配丢弃
                if (findQuery.isNotEmpty()) {
                    val newMatches = pending.filter { it.seq >= oldest && it.raw.contains(findQuery, ignoreCase = true) }
                        .map { it.seq }
                    matchSeqs = matchSeqs.dropWhile { it < oldest } + newMatches
                }
                pending.clear()
            }

            val flusher = launch {
                while (true) {
                    delay(150)
                    flush()
                }
            }
            try {
                logcatStream.logcatFlow(deviceIp).collect { entry -> pending.add(entry) }
            } finally {
                flusher.cancel()
                flush()
                logcatStream.stop()
                isRunning = false
            }
        }
    }

    fun stopLogcat() {
        logcatJob?.cancel()
        logcatStream.stop()
        logcatJob = null
        isRunning = false
    }

    // 清空信号：停采集并清缓冲与显示视图（放在 stopLogcat 定义之后引用）

    fun closeFindBar() {
        findBarVisible = false
        findQuery = ""
        matchSeqs = emptyList()
        currentMatchSeq = null
    }

    /** 前后跳转匹配：当前匹配被预算淘汰时沿方向取最近存活匹配，到头回绕 */
    fun jumpMatch(forward: Boolean) {
        val seqs = matchSeqs
        if (seqs.isEmpty()) return
        val target = when (val cur = currentMatchSeq) {
            null -> seqs.first()
            else -> {
                val idx = seqs.binarySearch(cur)
                if (idx >= 0) {
                    val next = if (forward) idx + 1 else idx - 1
                    if (next in seqs.indices) seqs[next]
                    else if (forward) seqs.first() else seqs.last()
                } else {
                    val ins = -(idx + 1) // 插入点：当前匹配已被淘汰
                    when {
                        forward && ins < seqs.size -> seqs[ins]
                        forward -> seqs.first()
                        ins > 0 -> seqs[ins - 1]
                        else -> seqs.last()
                    }
                }
            }
        }
        currentMatchSeq = target
        val pos = positionOf(logEntries, target)
        if (pos >= 0) scope.launch { listState.scrollToItem(pos) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            // Ctrl+F 打开/聚焦 FindBar（面板内任意子项持有焦点时生效）；
            // 行选模式下 Ctrl+C 复制选中行区间、Esc 清除行选
            .onPreviewKeyEvent { event ->
                val lineRangeActive = lineSelectMode && lineSelectRange() != null
                when {
                    event.type == KeyEventType.KeyDown && event.isCtrlPressed && event.key == Key.F -> {
                        if (findBarVisible) {
                            runCatching { findInputFocus.requestFocus() }
                        } else {
                            findBarVisible = true // 聚焦由 LaunchedEffect(findBarVisible) 组合完成后处理
                        }
                        true
                    }
                    event.type == KeyEventType.KeyDown && event.isCtrlPressed &&
                        event.key == Key.C && lineRangeActive -> {
                        copyLineSelection(); true
                    }
                    // 右键菜单打开时 Esc 关菜单（菜单 Popup 非 focusable，键盘事件走面板根）
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape && contextMenuVisible -> {
                        contextMenuVisible = false; true
                    }
                    event.type == KeyEventType.KeyDown && event.key == Key.Escape && lineRangeActive -> {
                        lineSelectAnchor = null; lineSelectEnd = null; true
                    }
                    else -> false
                }
            }
    ) {
        // ── 控制栏（P12 翻新：Surface 底 + token 间距） ──
        Surface(
            modifier = Modifier.fillMaxWidth(),
            elevation = 0.dp,
            color = easyAdbColors.Surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 设备信息
                    Text(
                        text = deviceIp ?: "未选中设备",
                        fontSize = 10.sp,
                        color = easyAdbColors.TextSecondary
                    )
                    Spacer(modifier = Modifier.width(easyAdbTokens.spaceSm))

                    // 日志级别下拉
                    Box {
                        Button(
                            onClick = { levelDropdownExpanded = true },
                            modifier = Modifier.height(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                backgroundColor = easyAdbColors.PanelHeader,
                                contentColor = easyAdbColors.TextPrimary
                            ),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                            shape = easyAdbTokens.shapeSm
                        ) {
                            Text(selectedLevel.shortName, fontSize = 10.sp)
                            Icon(Icons.Default.ArrowDropDown, null, modifier = Modifier.width(14.dp).height(14.dp))
                        }
                        DropdownMenu(
                            expanded = levelDropdownExpanded,
                            onDismissRequest = { levelDropdownExpanded = false }
                        ) {
                            levelOptions.forEach { level ->
                                DropdownMenuItem(onClick = {
                                        selectedLevel = level
                                        levelDropdownExpanded = false
                                    },
                                    modifier = Modifier.height(28.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                        horizontal = 12.dp,
                                        vertical = 1.dp
                                    )
                                ) {
                                    Text(
                                        "${level.name} (${level.shortName})",
                                        fontSize = 12.sp,
                                        color = logColor(level.value)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(easyAdbTokens.spaceXs))

                    // 过滤输入框：条件即状态，显示视图随输入实时全量重滤
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        placeholder = { Text("过滤", fontSize = 10.sp) },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 24.dp),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        trailingIcon = {
                            if (searchText.isNotEmpty()) {
                                IconButton(
                                    onClick = { searchText = "" },
                                    modifier = Modifier.width(20.dp).height(20.dp)
                                ) {
                                    Icon(Icons.Default.Close, "清除", modifier = Modifier.width(14.dp).height(14.dp))
                                }
                            }
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "过滤后 ${logEntries.size} · 全量 ${logcatStream.buffer.size} 条",
                        fontSize = 9.sp,
                        color = easyAdbColors.TextHint
                    )
                    Spacer(modifier = Modifier.width(easyAdbTokens.spaceSm))
                    Text(
                        text = "缓冲 ${logcatStream.buffer.used / 1024} / ${logcatStream.buffer.budget / 1024} KB",
                        fontSize = 9.sp,
                        color = easyAdbColors.TextHint
                    )
                    lineSelectRange()?.let { range ->
                        Spacer(modifier = Modifier.width(easyAdbTokens.spaceSm))
                        Text(
                            text = "行选 ${logEntries.count { it.seq in range }} 条",
                            fontSize = 9.sp,
                            color = easyAdbColors.TextHint
                        )
                    }
                }
            }
        }

        // ── Find 工具条（Ctrl+F 打开，Esc 关闭；P12 翻新：Surface 底 + 轻浮起 token） ──
        if (findBarVisible) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                elevation = easyAdbTokens.elevationLow,
                color = easyAdbColors.Surface
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = findQuery,
                        onValueChange = { findQuery = it },
                        placeholder = { Text("查找（Enter 下一个 · Shift+Enter 上一个 · Esc 关闭）", fontSize = 10.sp) },
                        singleLine = true,
                        // 输入框固定 1/3 宽度，计数与按钮紧随其后，FindBar 右侧留空
                        modifier = Modifier
                            .fillMaxWidth(0.33f)
                            .heightIn(min = 28.dp)
                            .focusRequester(findInputFocus)
                            .onPreviewKeyEvent { event ->
                                when {
                                    event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                        closeFindBar(); true
                                    }
                                    event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                        jumpMatch(forward = !event.isShiftPressed); true
                                    }
                                    else -> false
                                }
                            },
                        textStyle = androidx.compose.ui.text.TextStyle(
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    )
                    Spacer(modifier = Modifier.width(easyAdbTokens.spaceXs))
                    // 计数 cur/total：流式运行中追加 "+"，表示匹配总数随新日志持续增长
                    Text(
                        text = buildString {
                            val cur = currentMatchSeq?.let { matchSeqs.binarySearch(it) }?.takeIf { it >= 0 }
                            append("${(cur?.plus(1)) ?: 0}/${matchSeqs.size}")
                            if (isRunning) append("+")
                        },
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = easyAdbColors.TextSecondary,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                    IconButton(onClick = { jumpMatch(false) }, modifier = Modifier.width(24.dp).height(24.dp)) {
                        Icon(Icons.Default.ArrowUpward, "上一个匹配", modifier = Modifier.width(16.dp).height(16.dp))
                    }
                    IconButton(onClick = { jumpMatch(true) }, modifier = Modifier.width(24.dp).height(24.dp)) {
                        Icon(Icons.Default.ArrowDownward, "下一个匹配", modifier = Modifier.width(16.dp).height(16.dp))
                    }
                    IconButton(onClick = { closeFindBar() }, modifier = Modifier.width(24.dp).height(24.dp)) {
                        Icon(Icons.Default.Close, "关闭查找", modifier = Modifier.width(16.dp).height(16.dp))
                    }
                }
            }
        }

        // ── 左侧工具栏 + 右侧内容区 ──
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // 左侧垂直工具栏（始终可见，P12 翻新：Surface 底与 ConsolePanel 对齐）
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight()
                    .background(easyAdbColors.Surface),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                // ── 第1组：数据操作（清空 · 开始/停止采集）──
                TooltipIconButton(
                    tooltip = "清空日志",
                    onClick = {
                        logEntries.clear()
                        logcatStream.clearDisplay()
                    }
                ) {
                    Icon(Icons.Default.Delete, null, tint = easyAdbColors.TextSecondary, modifier = Modifier.width(18.dp).height(18.dp))
                }
                TooltipIconButton(
                    tooltip = if (isRunning) "停止采集" else "开始采集",
                    onClick = { if (isRunning) stopLogcat() else startLogcat() },
                    enabled = !isRunning && deviceIp != null || isRunning
                ) {
                    Icon(
                        if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
                        null,
                        tint = if (isRunning) easyAdbColors.LogError else easyAdbColors.StatusOnline,
                        modifier = Modifier.width(18.dp).height(18.dp)
                    )
                }

                ToolbarDivider()

                // ── 第2组：视图模式（自动滚动 · 行选模式）──
                TooltipIconButton(
                    tooltip = if (autoScroll) "自动滚动（点击关闭）" else "自动滚动（点击开启）",
                    onClick = { autoScroll = !autoScroll }
                ) {
                    ModeIcon(Icons.Default.KeyboardDoubleArrowDown, autoScroll)
                }
                TooltipIconButton(
                    tooltip = if (lineSelectMode) "行选模式（点击关闭）" else "行选模式（点击行设锚点，Shift+点击扩展区间）",
                    onClick = {
                        lineSelectMode = !lineSelectMode
                        if (!lineSelectMode) { lineSelectAnchor = null; lineSelectEnd = null }
                    }
                ) {
                    ModeIcon(Icons.Default.ListAlt, lineSelectMode)
                }

                ToolbarDivider()

                // ── 第3组：日志导出/导入（占位，功能后续实现）──
                TooltipIconButton(tooltip = "导出日志（待实现）", enabled = false, onClick = {}) {
                    Icon(Icons.Default.FileDownload, null, tint = easyAdbColors.TextDisabled, modifier = Modifier.width(18.dp).height(18.dp))
                }
                TooltipIconButton(tooltip = "导入日志（待实现）", enabled = false, onClick = {}) {
                    Icon(Icons.Default.FileUpload, null, tint = easyAdbColors.TextDisabled, modifier = Modifier.width(18.dp).height(18.dp))
                }
            }

            // 右侧内容区：未选中设备显示空态，选中设备显示日志列表
            if (deviceIp == null) {
                Box(
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = Icons.Default.Devices,
                        title = "未选择设备",
                        subtitle = "请先在左侧设备列表中选择一个设备，再开始采集日志"
                    )
                }
            } else {
                // 外层 Box 承载右键菜单锚点；滚动条与右键菜单 Popup 与 SelectionContainer
                // 平级——Popup 挂在 SelectionContainer 子树内时，菜单项点击事件会在主窗口
                // 与弹窗两棵组合树间路由，坐标换算抛 "layouts are not part of the same
                // hierarchy"（NodeCoordinator.findCommonAncestor 跨窗口无共同祖先）。
                // 另：桌面版 SelectionContainer 自带右键「复制/全选」默认菜单，用空实现
                // 覆写 LocalTextContextMenu 抑制之
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        // 右键弹出快捷菜单（复制/清空），记录按下位置（px）作为菜单锚点
                        .onPointerEvent(PointerEventType.Press) { event ->
                            if (event.buttons.isSecondaryPressed) {
                                val pos = event.changes.first().position
                                contextMenuOffset = IntOffset(pos.x.roundToInt(), pos.y.roundToInt())
                                contextMenuVisible = true
                            }
                        }
                ) {
                    val lineSelRange = lineSelectRange()
                    CompositionLocalProvider(LocalTextContextMenu provides NoTextContextMenu) {
                        key(selectionGeneration) {
                            SelectionContainer {
                                LazyColumn(
                                    state = listState,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(horizontal = 4.dp)
                                        // 列表主键按下（Initial 父→子遍历）：行选模式做整行区间
                                        // 选择并阻断字级手势；普通模式下若视口自上次选择按下后
                                        // 变化过，先换代重建 SelectionContainer 清悬空选区并吞掉
                                        // 本次按下（防 Compose 1.7 悬空 id 崩溃，再按一次正常选择）。
                                        // 挂 LazyColumn 而非外层 Box：滚动条是兄弟节点不在事件
                                        // 路由上，其拖拽不受任何影响
                                        .onPointerEvent(PointerEventType.Press, PointerEventPass.Initial) { event ->
                                            if (event.buttons.isPrimaryPressed) {
                                                val mark = pressViewportVersion
                                                pressViewportVersion = viewportVersion
                                                if (lineSelectMode) {
                                                    val seq = seqAtPosition(listState, event.changes.first().position.y)
                                                    if (seq != null) {
                                                        if (event.keyboardModifiers.pointerIsShiftPressed && lineSelectAnchor != null) {
                                                            lineSelectEnd = seq
                                                        } else {
                                                            lineSelectAnchor = seq
                                                            lineSelectEnd = seq
                                                        }
                                                        event.changes.forEach { it.consume() }
                                                    }
                                                } else if (mark >= 0 && mark != viewportVersion) {
                                                    selectionGeneration++
                                                    event.changes.forEach { it.consume() }
                                                }
                                            }
                                        }
                                        // Ctrl+滚轮缩放字号；消费掉滚轮事件避免列表同时滚动
                                        .pointerInput(Unit) {
                                            awaitPointerEventScope {
                                                while (true) {
                                                    val event = awaitPointerEvent()
                                                    if (event.type == PointerEventType.Scroll &&
                                                        event.keyboardModifiers.pointerIsCtrlPressed
                                                    ) {
                                                        val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                                                        if (delta != 0f) {
                                                            logFontSize = (logFontSize.value + if (delta < 0f) 1f else -1f)
                                                                                .coerceIn(9f, 20f).sp
                                                            event.changes.forEach { it.consume() }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                ) {
                                    // seq 作稳定 key：内容 key 在重复行下必撞 key 崩溃，下标 key 在头部
                                    // 淘汰时全列表错位；seq 单调唯一，淘汰/追加下 LazyColumn 可精确增量更新
                                    itemsIndexed(logEntries, key = { _, it -> it.seq }) { _, entry ->
                                        val annotated = if (findQuery.isEmpty()) null
                                            else buildHighlighted(entry.raw, findQuery, entry.seq == currentMatchSeq)
                                        val inLineSel = lineSelRange != null && entry.seq in lineSelRange
                                        Text(
                                            text = annotated ?: AnnotatedString(entry.raw),
                                            fontSize = logFontSize,
                                            fontFamily = FontFamily.Monospace,
                                            color = logColor(entry.level.value),
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            // fillMaxWidth 撑满整行 + padding 内置：短行右侧空白也能起选；
                                            // 行选区间整行铺选中底色
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (inLineSel) easyAdbColors.Selected else Color.Transparent
                                                )
                                                .padding(vertical = 1.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 右侧滚动条：滑块随列表实时联动，支持拖动滑块与点击轨道滚动；
                    // hover 到滚动条区域（12dp 宽轨道）时显示淡底色突出
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .width(12.dp)
                            .background(if (scrollbarHovered) easyAdbColors.Hover else Color.Transparent)
                            .onPointerEvent(PointerEventType.Enter) { scrollbarHovered = true }
                            .onPointerEvent(PointerEventType.Exit) { scrollbarHovered = false }
                    )

                    // 右键快捷菜单：复制=当前过滤视图全部内容写剪贴板（选中文字仍走 Ctrl+C），
                    // 清空=与左侧工具栏清空按钮同款行为。Popup(TopStart) 锚定点击点固定向下
                    // 展开（material DropdownMenu 在下方空间不足时会向上翻折）；不用 focusable
                    // ——焦点弹窗的事件路由是跨组合树坐标崩溃的另一半诱因，外点关闭由
                    // onDismissRequest 承担、Esc 关闭走面板根快捷键
                    if (contextMenuVisible) {
                        Popup(
                            alignment = Alignment.TopStart,
                            offset = contextMenuOffset,
                            onDismissRequest = { contextMenuVisible = false }
                        ) {
                            Surface(
                                shape = easyAdbTokens.shapeLg,
                                elevation = easyAdbTokens.elevationMid
                            ) {
                                Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                                    // 行选区间存在时优先提供区间复制
                                    lineSelectRange()?.let { range ->
                                        ContextMenuItemRow(
                                            "复制选中行（${logEntries.count { it.seq in range }} 条）"
                                        ) {
                                            contextMenuVisible = false
                                            copyLineSelection()
                                        }
                                    }
                                    ContextMenuItemRow("复制日志（当前过滤视图）") {
                                        contextMenuVisible = false
                                        clipboard.setText(
                                            AnnotatedString(logEntries.joinToString("\n") { it.raw })
                                        )
                                    }
                                    ContextMenuItemRow("清空日志") {
                                        contextMenuVisible = false
                                        logEntries.clear()
                                        logcatStream.clearDisplay()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** seq → 列表位置（logEntries 按 seq 升序，二分查找）；未找到（已被淘汰/过滤）返回 -1 */
private fun positionOf(entries: List<LogcatEntry>, seq: Long): Int =
    entries.binarySearch { it.seq.compareTo(seq) }

/** 视口内点击纵坐标 → 行 seq（item key 即 seq；null 表示点在行间隙或视口外） */
private fun seqAtPosition(listState: LazyListState, y: Float): Long? {
    val info = listState.layoutInfo.visibleItemsInfo.firstOrNull {
        y >= it.offset && y < it.offset + it.size
    }
    return info?.key as? Long
}

/** 展示层过滤：级别 + 关键字纯比较（解析在发射端完成，重滤无需 regex）。PID 过滤已立项后续 */
private fun accepts(entry: LogcatEntry, level: LogLevel, keyword: String): Boolean =
    entry.level.value >= level.value &&
        (keyword.isEmpty() || keyword in entry.raw)

/**
 * [TextContextMenu] 的空实现：不包 ContextMenuArea，直接渲染内容——用于抑制桌面版
 * SelectionContainer 自带的右键「复制/全选」默认菜单（日志区右键只出面板自定义菜单）
 */
private object NoTextContextMenu : TextContextMenu {
    @Composable
    override fun Area(
        textManager: TextContextMenu.TextManager,
        state: ContextMenuState,
        content: @Composable () -> Unit
    ) {
        content()
    }
}

/** 右键菜单行：在 IntrinsicSize.Min 等宽列内自适应，hover 淡底色、无涟漪点击 */
@Composable
private fun ContextMenuItemRow(label: String, onClick: () -> Unit) {
    var hovered by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .background(if (hovered) easyAdbColors.Hover else Color.Transparent)
            .onPointerEvent(PointerEventType.Enter) { hovered = true }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .clickable(
                interactionSource = null,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(label, fontSize = 12.sp, color = easyAdbColors.TextPrimary)
    }
}

/**
 * 工具栏按钮 + hover 提示：鼠标悬停 ≥300ms 在按钮右侧弹出一个小气泡显示 [tooltip]。
 * CMP 1.7.3 无 TooltipArea，用 Box + Popup 自建。气泡左缘锚定按钮左缘并向右偏移
 * 一个按钮宽（CenterEnd + 向左伸展会把气泡盖回按钮上，指针进出气泡边界导致闪烁）
 */
@Composable
private fun TooltipIconButton(
    tooltip: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    content: @Composable () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    var showTip by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    LaunchedEffect(hovered) {
        if (hovered) {
            delay(300)
            showTip = true
        } else {
            showTip = false
        }
    }
    Box {
        IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .width(28.dp)
                .height(28.dp)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
        ) { content() }
        if (showTip) {
            // 向右偏移一个按钮宽（28dp）+ 4dp 间隙，气泡整体落在按钮右侧
            val offsetX = with(density) { 32.dp.roundToPx() }
            Popup(alignment = Alignment.CenterStart, offset = IntOffset(offsetX, 0)) {
                Surface(
                    shape = easyAdbTokens.shapeSm,
                    elevation = easyAdbTokens.elevationMid,
                    color = easyAdbColors.TooltipBackground
                ) {
                    Text(
                        tooltip,
                        color = easyAdbColors.TooltipText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** 工具栏分组分隔线 */
@Composable
private fun ToolbarDivider() {
    Box(
        modifier = Modifier
            .width(20.dp)
            .height(1.dp)
            .background(easyAdbColors.Divider)
            .padding(vertical = 3.dp)
    )
}

/** 视图模式开关图标：激活态淡主题色圆角底框（token 圆角）+ 主题色图标 */
@Composable
private fun ModeIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean) {
    Icon(
        icon,
        null,
        tint = if (active) easyAdbColors.Primary else easyAdbColors.TextSecondary,
        modifier = Modifier
            .background(
                if (active) easyAdbColors.Primary.copy(alpha = 0.15f) else Color.Transparent,
                easyAdbTokens.shapeSm
            )
            .padding(3.dp)
            .width(18.dp).height(18.dp)
    )
}

/** 查询命中片段高亮：普通匹配浅黄底，当前匹配深琥珀底；大小写不敏感（v1 从简，无正则） */
@Composable
private fun buildHighlighted(raw: String, query: String, isCurrent: Boolean): AnnotatedString =
    buildAnnotatedString {
        append(raw)
        var from = 0
        while (true) {
            val idx = raw.indexOf(query, startIndex = from, ignoreCase = true)
            if (idx < 0) break
            addStyle(
                SpanStyle(background = if (isCurrent) easyAdbColors.FindCurrent else easyAdbColors.FindHighlight),
                idx, idx + query.length
            )
            from = idx + query.length
        }
    }