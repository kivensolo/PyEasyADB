@file:OptIn(androidx.compose.material.ExperimentalMaterialApi::class)

package com.easyadb.ui.functions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.FunctionItem
import com.easyadb.ui.designsystem.apkDropTarget
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/** 文件传输类功能（推送/拉取文件）：点击经 [onFileItemClick] 跳转文件传输 Tab（SP-5001 起传输统一走双栏面板） */
private val FILE_TRANSFER_ACTIONS = setOf("m_push_file", "m_pull_file")

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：功能卡网格（P12 布局大改 / 快捷操作 Tab 对齐 HTML 参照稿）。
 *              「安装应用」卡特殊化为虚线拖拽卡（拖入 APK 回调 [onApkDropped]，
 *              点击走普通 onItemClick 打开安装对话框）；推送/拉取文件卡点击经
 *              [onFileItemClick]（跳转文件传输 Tab）；其余小卡经 [onItemClick]。
 *              原 tab=files 三大卡与 FileTransferCard 已随 SP-5001 双栏面板移除。
 *              对应 Python `src/CenterWindow.py::_setUpUIDynamic`。
 */
@Composable
fun TemplateGrid(
    items: List<FunctionItem>,
    onItemClick: (FunctionItem) -> Unit,
    onFileItemClick: (FunctionItem, devicePath: String) -> Unit,
    onApkDropped: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val everyRowSize = 4

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        val rows = (items.size + everyRowSize - 1) / everyRowSize
        for (rowIndex in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                for (colIndex in 0 until everyRowSize) {
                    val itemIndex = rowIndex * everyRowSize + colIndex
                    if (itemIndex < items.size) {
                        val item = items[itemIndex]
                        when {
                            // 安装应用：虚线拖拽卡（拖入 APK 直接安装，点击开对话框）
                            item.action == "m_show_install_app_dialog" -> InstallApkCard(
                                item = item,
                                isDisabled = item.state == "disable",
                                onClick = { onItemClick(item) },
                                onApkDropped = onApkDropped,
                                modifier = Modifier.weight(1f)
                            )
                            // 推送/拉取文件：普通小卡，点击跳转文件传输 Tab（SP-5001）
                            item.action in FILE_TRANSFER_ACTIONS -> FunctionCard(
                                item = item,
                                isDisabled = item.state == "disable",
                                onClick = { onFileItemClick(item, "") },
                                modifier = Modifier.weight(1f)
                            )
                            else -> FunctionCard(
                                item = item,
                                isDisabled = item.state == "disable",
                                onClick = { onItemClick(item) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        // 补位空 Spacer，保持卡片等宽对齐
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 通用功能小卡：shapeLg 描边卡 + 语义色图标块（accent 12% 底 + 25% 描边）
 * + 标题/副文案两行文本列。图标沿用 function_templates.xml 既有 PNG 资源
 * （不矢量化不染色）。
 */
@Composable
private fun FunctionCard(
    item: FunctionItem,
    isDisabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accent = accentColor(item.accent, easyAdbColors)

    Surface(
        onClick = onClick,
        enabled = !isDisabled,
        interactionSource = interactionSource,
        modifier = modifier.heightIn(min = 76.dp),
        shape = easyAdbTokens.shapeLg,
        color = if (isHovered && !isDisabled) easyAdbColors.Hover else easyAdbColors.Surface,
        border = BorderStroke(
            easyAdbTokens.strokeWidth,
            if (isHovered && !isDisabled) easyAdbColors.Primary.copy(alpha = 0.5f) else easyAdbColors.Divider
        ),
        elevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AccentIconBox(
                icon = tileIconForItem(item),
                contentDescription = item.text,
                accent = accent
            )
            Spacer(modifier = Modifier.width(10.dp))
            CardTextColumn(item = item, isDisabled = isDisabled, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * 安装应用虚线拖拽卡（对齐 HTML 参照稿 dz 虚线卡）：
 * 虚线描边（drawBehind + dashPathEffect 画圆角虚线矩形，Compose 无内置
 * dashed border）；拖拽悬停时底色淡蓝高亮、虚线变主色实色；拖入合法 APK
 * 回调 [onApkDropped]；点击行为不变（打开安装对话框）。
 */
@Composable
private fun InstallApkCard(
    item: FunctionItem,
    isDisabled: Boolean,
    onClick: () -> Unit,
    onApkDropped: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    var dragHover by remember { mutableStateOf(false) }
    val accent = accentColor(item.accent, easyAdbColors)
    val dashColor = when {
        dragHover -> easyAdbColors.Primary
        isHovered && !isDisabled -> easyAdbColors.Primary.copy(alpha = 0.5f)
        else -> easyAdbColors.Border
    }
    val strokeWidth = easyAdbTokens.strokeWidth

    Surface(
        onClick = onClick,
        enabled = !isDisabled,
        interactionSource = interactionSource,
        modifier = modifier
            .heightIn(min = 76.dp)
            .apkDropTarget(
                onDragHover = { dragHover = it },
                onApkDropped = onApkDropped
            ),
        shape = easyAdbTokens.shapeLg,
        color = when {
            dragHover -> easyAdbColors.Primary.copy(alpha = 0.06f)
            isHovered && !isDisabled -> easyAdbColors.Hover
            else -> easyAdbColors.Surface
        },
        elevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // 圆角虚线描边：内缩半个描边宽，避免被 Surface 的 shape 裁剪
                    val strokePx = strokeWidth.toPx()
                    drawRoundRect(
                        color = dashColor,
                        topLeft = Offset(strokePx / 2, strokePx / 2),
                        size = Size(size.width - strokePx, size.height - strokePx),
                        cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx()),
                        style = Stroke(
                            width = strokePx,
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(6.dp.toPx(), 4.dp.toPx())
                            )
                        )
                    )
                }
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AccentIconBox(
                    icon = tileIconForItem(item),
                    contentDescription = item.text,
                    accent = accent
                )
                Spacer(modifier = Modifier.width(10.dp))
                CardTextColumn(item = item, isDisabled = isDisabled, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** 语义色图标块：accent 12% 透明底 + 25% 透明描边，线稿图标染 accent 色；无 accent 时染 AccentGrey（对齐 HTML a-icon.gray 素灰）。 */
@Composable
internal fun AccentIconBox(
    icon: ImageVector,
    contentDescription: String,
    accent: Color?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = easyAdbTokens.shapeMd,
        color = accent?.copy(alpha = 0.12f) ?: easyAdbColors.Selected,
        border = accent?.let { BorderStroke(easyAdbTokens.strokeWidth, it.copy(alpha = 0.25f)) },
        elevation = 0.dp,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.padding(6.dp).size(26.dp),
            tint = accent ?: easyAdbColors.AccentGrey
        )
    }
}

/** 卡片文本列（对齐 HTML tile）：标题 + 副文案（XML desc，非空才显示），均单行省略。 */
@Composable
private fun CardTextColumn(
    item: FunctionItem,
    isDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = item.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (!isDisabled) easyAdbColors.TextPrimary else easyAdbColors.TextDisabled
        )
        if (item.desc.isNotBlank()) {
            Spacer(Modifier.height(3.dp))
            Text(
                text = item.desc,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (!isDisabled) easyAdbColors.TextSecondary else easyAdbColors.TextDisabled
            )
        }
    }
}
