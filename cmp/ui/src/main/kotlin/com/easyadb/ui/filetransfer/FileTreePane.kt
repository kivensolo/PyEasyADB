package com.easyadb.ui.filetransfer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.file.FileEntry
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.EasyAdbTileIcons
import com.easyadb.ui.designsystem.EmptyState
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground
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
 */
@Composable
fun FileTreePane(
    state: FileTreeState,
    pc: Boolean,
    title: String,
    osIconTint: Color,
    modifier: Modifier = Modifier,
    toolbar: @Composable RowScope.() -> Unit = {},
    dropModifier: Modifier = Modifier
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
                val dispPath = (state.selectedEntry?.path ?: state.rootPath)
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
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
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
                    val listState = rememberLazyListState()
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)
                    ) {
                        items(state.visibleRows, key = { it.entry.path }) { row ->
                            TreeRow(row, pc, state)
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(listState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(10.dp).padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

/** 树行：缩进 + 折叠箭头（旋转动画）+ 类型图标 + 名称 + 元数据列；选中态左缘竖条 */
@Composable
private fun TreeRow(row: FileTreeState.VisibleRow, pc: Boolean, state: FileTreeState) {
    val entry = row.entry
    val selected = state.selectedEntry?.path == entry.path
    val caretAngle by animateFloatAsState(
        targetValue = if (entry.path in state.expandedPaths) 90f else 0f,
        animationSpec = tween(150),
        label = "caret"
    )
    val interaction = remember { MutableInteractionSource() }
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
            .clickable(interactionSource = interaction, indication = null) { state.toggle(entry) }
            .padding(start = 8.dp + 16.dp * row.depth, end = 10.dp)
    ) {
        // 折叠箭头 / 占位
        Box(modifier = Modifier.width(16.dp), contentAlignment = Alignment.Center) {
            if (entry.isDir) {
                Icon(
                    imageVector = AppIcons.ArrowRight,
                    contentDescription = if (entry.path in state.expandedPaths) "收起" else "展开",
                    tint = easyAdbColors.TextHint,
                    modifier = Modifier.size(12.dp).rotate(caretAngle)
                )
            }
        }
        Icon(
            imageVector = if (entry.isDir) EasyAdbTileIcons.Folder else EasyAdbTileIcons.FileDoc,
            contentDescription = null,
            tint = if (entry.isDir) easyAdbColors.PrimaryLight else easyAdbColors.TextHint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            entry.name,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = easyAdbColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
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
