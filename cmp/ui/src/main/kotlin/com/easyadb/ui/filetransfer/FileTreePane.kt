@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.filetransfer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.easyadb.core.file.FileEntry
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground
import kotlin.math.roundToInt
import java.awt.Cursor

/**
 * 通用文件树面板（SP-5001）——PC / 设备双栏共用，对齐 H5 pane-files 终稿。
 *
 * 结构：头部（OS 徽章 + 标题 + 选中路径）→ 工具栏（slot 注入）→ 列头 →
 * LazyColumn 扁平化可见行（key = path，目录优先排序由 lister 保证）。
 * [pc] 控制列数（三列，无权限位）与路径分隔符展示；设备侧四列含权限。
 *
 * @param toolbar 工具栏内容（RowScope：投递/新建/另存/推送/删除/刷新等由上层注入）
 * @param dropModifier 外层附加修饰符（设备侧拖放接收由上层包在 [modifier] 上）
 * @param contextMenu 非空时行支持右键菜单：参数为（目标节点，关闭菜单回调），
 *   菜单以 Popup 锚定在按下位置（PC 侧「在资源管理器打开」等宿主动作由上层注入）
 * @param onCreateCommit 非空时启用就地新建目录：名称编辑行回车/失焦提交，
 *   参数为（父目录路径，新目录名），执行 mkdir 由上层承担
 */
@Composable
fun FileTreePane(
    state: FileTreeState,
    pc: Boolean,
    title: String,
    osIconTint: Color,
    modifier: Modifier = Modifier,
    toolbar: @Composable RowScope.() -> Unit = {},
    dropModifier: Modifier = Modifier,
    contextMenu: (@Composable (FileEntry, () -> Unit) -> Unit)? = null,
    onCreateCommit: ((parentPath: String, name: String) -> Unit)? = null
) {
    androidx.compose.material.Surface(
        shape = easyAdbTokens.shapeLg,
        color = easyAdbColors.Surface,
        elevation = 0.dp,
        border = androidx.compose.foundation.BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Divider),
        modifier = modifier.fillMaxSize().then(dropModifier)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 头部：OS 徽章 + 标题 + 选中节点完整路径 ──
            Row(
                modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(osIconTint.copy(alpha = if (pc) 0.13f else 0.12f))
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        if (pc) "PC" else "Android",
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = osIconTint,
                        letterSpacing = 1.sp
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = easyAdbColors.TextPrimary
                )
                Spacer(Modifier.width(12.dp))
                // 路径栏：选中节点完整路径（PC 侧反斜杠展示），未选中显示根
                val dispPath = (state.selectedEntry?.path ?: state.rootDisplay)
                    .let { if (pc) it.replace('/', '\\') else it }
                Text(
                    dispPath,
                    fontSize = 10.5.sp,
                    fontFamily = FontFamily.Monospace,
                    color = easyAdbColors.TextHint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End,
                    modifier = Modifier.weight(1f)
                )
            }
            HorizontalDivider()
            // ── 工具栏（slot 注入） ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = toolbar
            )
            HorizontalDivider()
            // ── 列头 ──
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("文件", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = easyAdbColors.TextSecondary, modifier = Modifier.weight(1f))
                if (!pc) {
                    Text("权限", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = easyAdbColors.TextSecondary, modifier = Modifier.width(96.dp))
                }
                Text("时间", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = easyAdbColors.TextSecondary, modifier = Modifier.width(104.dp))
                Text("大小", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = easyAdbColors.TextSecondary, textAlign = TextAlign.End, modifier = Modifier.width(64.dp))
            }
            HorizontalDivider()
            // ── 列表 ──
            // 右键菜单位置采用 LogcatPanel 同款方案：按压事件挂在容器 Box 上
            // （坐标天然相对容器），按 y 命中 LazyColumn 行项，Popup 作为容器子级
            // TopStart+offset 定位（行内锚定在部分环境偏移到面板底部，走查反馈弃用）
            val listState = rememberLazyListState()
            var ctxEntry by remember { mutableStateOf<FileEntry?>(null) }
            var ctxPos by remember { mutableStateOf(Offset.Zero) }
            val density = LocalDensity.current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .let { m ->
                        if (contextMenu == null) m else m.onPointerEvent(PointerEventType.Press) { event ->
                            if (event.buttons.isSecondaryPressed) {
                                val pos = event.changes.firstOrNull()?.position ?: return@onPointerEvent
                                // LazyColumn 外层 vertical padding 4dp：命中测试前扣除
                                val yInList = pos.y - with(density) { 4.dp.toPx() }
                                val hitKey = listState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { yInList >= it.offset && yInList < it.offset + it.size }
                                    ?.key as? String ?: return@onPointerEvent
                                val hit = state.visibleRows.firstOrNull {
                                    it.entry.path == hitKey && it.kind == FileTreeState.RowKind.NORMAL
                                } ?: return@onPointerEvent
                                state.selectedEntry = hit.entry
                                ctxEntry = hit.entry
                                ctxPos = pos
                            }
                        }
                    }
            ) {
                if (state.visibleRows.isEmpty() && state.loadingPath == state.rootPath) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                    }
                } else if (state.visibleRows.isEmpty()) {
                    EmptyState(
                        icon = EasyAdbTileIcons.Folder,
                        title = "此文件夹为空",
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(state.visibleRows, key = { it.entry.path }) { row ->
                            TreeRow(row, pc, state, contextMenu, onCreateCommit)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp).padding(vertical = 4.dp)
                    )
                }
                ctxEntry?.let { entry ->
                    Popup(
                        alignment = Alignment.TopStart,
                        offset = IntOffset(ctxPos.x.roundToInt(), ctxPos.y.roundToInt()),
                        onDismissRequest = { ctxEntry = null },
                        focusable = true
                    ) {
                        contextMenu?.invoke(entry) { ctxEntry = null }
                    }
                }
            }
        }
    }
}

/** 树行：缩进 + 折叠箭头（旋转动画）+ 类型图标 + 名称 + 元数据列；选中态左缘竖条 */
@Composable
private fun TreeRow(
    row: FileTreeState.VisibleRow,
    pc: Boolean,
    state: FileTreeState,
    contextMenu: (@Composable (FileEntry, () -> Unit) -> Unit)? = null,
    onCreateCommit: ((parentPath: String, name: String) -> Unit)? = null
) {
    val entry = row.entry
    // 就地新建目录：名称编辑行（回车/失焦提交，Esc 取消）
    if (row.kind == FileTreeState.RowKind.CREATING) {
        val parentPath = row.entry.path.removeSuffix("/<creating>")
        CreatingRow(row, state, parentPath, onCreateCommit)
        return
    }
    // 提示行（列举失败原因 / 空目录）：单行文本对齐名称列，无交互、无元数据列
    if (row.kind != FileTreeState.RowKind.NORMAL) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
                .padding(start = 8.dp + 16.dp * row.depth + 38.dp, end = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                row.entry.name,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = if (row.kind == FileTreeState.RowKind.ERROR) easyAdbColors.LogError else easyAdbColors.TextHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }
    val selected = state.selectedEntry?.path == entry.path
    val caretAngle by animateFloatAsState(
        targetValue = if (entry.path in state.expandedPaths) 90f else 0f,
        animationSpec = tween(150),
        label = "caret"
    )
    // drawBehind 块非组合环境，颜色须在组合期捕获
    val selBg = easyAdbColors.Selected
    val selBar = easyAdbColors.Primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(easyAdbTokens.shapeSm)
            .background(if (selected) selBg else Color.Transparent)
            .drawBehind {
                if (selected) {
                    drawLine(
                        color = selBar,
                        start = Offset(0f, 4.dp.toPx()),
                        end = Offset(0f, size.height - 4.dp.toPx()),
                        strokeWidth = 2.dp.toPx()
                    )
                }
            }
            .hoverBackground(shape = easyAdbTokens.shapeSm)
            // 单击仅选中；双击展开/收起（折叠箭头另有独立点击，SP-5001 走查交互）
            .pointerInput(entry.path) {
                detectTapGestures(
                    onTap = { state.select(entry) },
                    onDoubleTap = {
                        state.select(entry)
                        state.toggleExpand(entry)
                    }
                )
            }
            .padding(start = 8.dp + 16.dp * row.depth, end = 10.dp)
    ) {
        // 折叠箭头 / 占位（独立点击：仅展开/收起，不改变选中）
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (entry.isDir) {
                Icon(
                    imageVector = AppIcons.ArrowRight,
                    contentDescription = if (entry.path in state.expandedPaths) "收起" else "展开",
                    tint = easyAdbColors.TextHint,
                    modifier = Modifier
                        .size(12.dp)
                        .rotate(caretAngle)
                        .clickable(interactionSource = null, indication = null) { state.toggleExpand(entry) }
                )
            }
        }
        // 目录符号链接：紫色文件夹 + 右上小箭头角标（普通目录为蓝色文件夹，一眼区分；
        // 紫与蓝色相最大——青色系与 PrimaryLight 蓝过近，走查反馈弃用）
        if (entry.isDir) {
            Box(modifier = Modifier.size(15.dp)) {
                Icon(
                    imageVector = EasyAdbTileIcons.Folder,
                    contentDescription = if (entry.isSymlink) "目录符号链接" else null,
                    tint = if (entry.isSymlink) easyAdbColors.AccentViolet else easyAdbColors.PrimaryLight,
                    modifier = Modifier.size(15.dp)
                )
                if (entry.isSymlink) {
                    Icon(
                        imageVector = AppIcons.NorthEast,
                        contentDescription = null,
                        tint = easyAdbColors.AccentViolet,
                        modifier = Modifier
                            .size(7.dp)
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-1).dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = EasyAdbTileIcons.FileDoc,
                contentDescription = null,
                tint = easyAdbColors.TextHint,
                modifier = Modifier.size(15.dp)
            )
        }
        Spacer(Modifier.width(7.dp))
        Text(
            entry.name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = easyAdbColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        // 目录符号链接：名称右侧展示真实指向（→ /storage/self/primary）
        entry.linkTarget?.let { target ->
            Spacer(Modifier.width(8.dp))
            Text(
                "→ $target",
                fontSize = 9.5.sp,
                fontFamily = FontFamily.Monospace,
                color = easyAdbColors.TextHint,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
        }
        // 该目录加载中：名称后小 spinner
        if (entry.isDir && entry.path == state.loadingPath && entry.path in state.expandedPaths) {
            CircularProgressIndicator(strokeWidth = 1.6.dp, modifier = Modifier.size(11.dp).padding(end = 4.dp))
            Spacer(Modifier.width(4.dp))
        }
        if (!pc) {
            Text(
                entry.perm ?: "",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = easyAdbColors.TextHint,
                maxLines = 1,
                modifier = Modifier.width(96.dp)
            )
        }
        Text(
            entry.modifiedAt ?: "",
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = easyAdbColors.TextHint,
            maxLines = 1,
            modifier = Modifier.width(104.dp)
        )
        Text(
            entry.sizeText,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            color = easyAdbColors.TextHint,
            maxLines = 1,
            textAlign = TextAlign.End,
            modifier = Modifier.width(64.dp)
        )
    }
}

@Composable
private fun HorizontalDivider() {
    androidx.compose.material.Divider(color = easyAdbColors.Divider, thickness = 1.dp)
}

/**
 * 就地新建目录编辑行：文件夹图标 + 自动聚焦名称输入框（Field 底 + Primary 描边）。
 * 回车 / 失焦提交（空名取消），Esc 取消；名称过滤 `/`（路径分隔符不可入名）。
 */
@Composable
private fun CreatingRow(
    row: FileTreeState.VisibleRow,
    state: FileTreeState,
    parentPath: String,
    onCreateCommit: ((parentPath: String, name: String) -> Unit)?
) {
    val focusRequester = remember { FocusRequester() }
    // 失焦提交守卫：onFocusChanged 初次组合即回调一次「未聚焦」，必须先聚焦过
    // 才把失焦视为提交（否则编辑行刚出现就用预填名自动 mkdir——走查踩坑）；
    // committed 防聚焦丢失双触发导致重复 mkdir
    var hadFocus by remember { mutableStateOf(false) }
    var committed by remember { mutableStateOf(false) }
    fun commit() {
        if (committed) return
        committed = true
        val name = state.creatingName.trim()
        val doCreate = name.isNotEmpty()
        state.cancelCreate()
        if (doCreate) onCreateCommit?.invoke(parentPath, name)
    }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(start = 8.dp + 16.dp * row.depth, end = 10.dp)
    ) {
        Spacer(Modifier.width(16.dp))
        Icon(
            imageVector = EasyAdbTileIcons.Folder,
            contentDescription = null,
            tint = easyAdbColors.PrimaryLight,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(24.dp)
                .background(easyAdbColors.Field, easyAdbTokens.shapeSm)
                .border(easyAdbTokens.strokeWidth, easyAdbColors.Primary, easyAdbTokens.shapeSm)
                .padding(horizontal = 6.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            androidx.compose.foundation.text.BasicTextField(
                value = state.creatingName,
                onValueChange = { state.creatingName = it.filterNot { c -> c == '/' } },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 12.sp,
                    color = easyAdbColors.TextPrimary
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(easyAdbColors.Primary),
                modifier = Modifier
                    .fillMaxSize()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        if (it.isFocused) hadFocus = true
                        else if (hadFocus) commit()
                    }
                    .onPreviewKeyEvent { event ->
                        when {
                            event.type == KeyEventType.KeyDown && event.key == Key.Enter -> {
                                commit(); true
                            }
                            event.type == KeyEventType.KeyDown && event.key == Key.Escape -> {
                                state.cancelCreate(); true
                            }
                            else -> false
                        }
                    }
            )
        }
    }
}
