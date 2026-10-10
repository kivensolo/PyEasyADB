package com.easyadb.ui.devicelist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.material.Divider
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.CmdGroup
import com.easyadb.core.config.CmdItem
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground

/**
 * 命令集面板（P12 webUI 重构）：master-detail 布局——左侧大类列表，右侧细项列表。
 *
 * 替代原侧栏三层折叠命令树：大类（Packages/Display/System/Network/About）在左列
 * 单选，细项在右侧平铺滚动；点击细项即执行（回调沿用原树单击语义）。
 * 位于内容区「设备管理」卡片的底部区域。
 */
@Composable
fun CommandPanel(
    cmdGroups: List<CmdGroup>,
    onCommandClick: (CmdItem) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedGroupName by remember(cmdGroups) { mutableStateOf(cmdGroups.firstOrNull()?.name) }
    val selectedGroup = cmdGroups.firstOrNull { it.name == selectedGroupName }
        ?: cmdGroups.firstOrNull()
    val items = selectedGroup?.let { group ->
        group.items + group.subGroups.flatMap { it.items }
    } ?: emptyList()

    Row(modifier = modifier.fillMaxWidth()) {
        // ── 左：大类列表 ──
        Column(
            modifier = Modifier
                .width(132.dp)
                .fillMaxHeight()
                .background(easyAdbColors.SurfaceVariant)
                .padding(vertical = 4.dp)
        ) {
            cmdGroups.forEach { group ->
                val selected = group === selectedGroup
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                        .height(30.dp)
                        .clip(easyAdbTokens.shapeMd)
                        .background(if (selected) easyAdbColors.Selected else Color.Transparent)
                        .clickable(interactionSource = null, indication = null) {
                            selectedGroupName = group.name
                        }
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .height(10.dp)
                                .clip(easyAdbTokens.shapeSm)
                                .background(easyAdbColors.Primary)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = group.name,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) easyAdbColors.Primary else easyAdbColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // 竖向分隔线
        Divider(
            color = easyAdbColors.Divider,
            modifier = Modifier.fillMaxHeight().width(1.dp)
        )

        // ── 右：选中大类的细项列表 ──
        Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
            if (items.isEmpty()) {
                Text(
                    text = "该分组暂无命令",
                    fontSize = 11.sp,
                    color = easyAdbColors.TextHint,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp)
                )
            } else {
                val listState = rememberLazyListState()
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 4.dp)
                ) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 1.dp)
                                .height(28.dp)
                                .clip(easyAdbTokens.shapeMd)
                                .hoverBackground()
                                .clickable(interactionSource = null, indication = null) {
                                    onCommandClick(item)
                                }
                                .padding(horizontal = 8.dp)
                                .horizontalScroll(rememberScrollState()),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.name,
                                fontSize = 12.sp,
                                color = easyAdbColors.TextPrimary
                            )
                            if (item.cmd.isNotBlank()) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = item.cmd,
                                    fontSize = 10.sp,
                                    color = easyAdbColors.TextHint,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
                VerticalScrollbar(
                    adapter = rememberScrollbarAdapter(listState),
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .width(5.dp)
                )
            }
        }
    }
}
