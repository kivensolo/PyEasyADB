package com.easyadb.ui.filetransfer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.Icon
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import com.easyadb.ui.designsystem.hoverBackground
import kotlinx.coroutines.delay

/**
 * 树面板工具栏按钮（SP-5001）：28dp 圆角图标按钮 + 悬停 ≥300ms 气泡提示。
 * 模式同 LogcatPanel TooltipIconButton / SysBadgeButton（CMP 1.7.3 无 TooltipArea 自建）；
 * 气泡向右伸展（工具栏居面板左侧，右向弹出落在面板空白区）。
 *
 * @param danger 危险动作（删除）：悬停红底
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TreeToolButton(
    icon: ImageVector,
    tip: String,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit
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
        // 禁用态：不加悬停高亮/点击——悬停亮底会误导可点（走查反馈）；
        // 但保留悬停追踪与气泡：置灰按钮「为什么不可点」恰恰需要提示说明（走查反馈）。
        // 图标用 TextDisabled 灰并降不透明度，与可点态一眼区分
        val modifier = if (enabled) {
            Modifier
                .size(28.dp)
                .clip(easyAdbTokens.shapeMd)
                .hoverBackground(
                    color = if (danger) easyAdbColors.AccentRed.copy(alpha = 0.14f) else null,
                    shape = easyAdbTokens.shapeMd
                )
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
                .clickable(interactionSource = null, indication = null, onClick = onClick)
        } else {
            Modifier
                .size(28.dp)
                .clip(easyAdbTokens.shapeMd)
                .onPointerEvent(PointerEventType.Enter) { hovered = true }
                .onPointerEvent(PointerEventType.Exit) { hovered = false }
        }
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = tip,
                tint = if (enabled) easyAdbColors.TextSecondary else easyAdbColors.TextDisabled.copy(alpha = 0.6f),
                modifier = Modifier.size(15.dp)
            )
        }
        if (showTip) {
            // 气泡左缘锚定按钮左缘 + 右偏 32dp（按钮宽 28 + 间隙 4），整体落在按钮右侧
            val offsetX = with(density) { 32.dp.roundToPx() }
            Popup(alignment = Alignment.CenterStart, offset = IntOffset(offsetX, 0)) {
                Surface(
                    shape = easyAdbTokens.shapeSm,
                    elevation = easyAdbTokens.elevationMid,
                    color = easyAdbColors.TooltipBackground
                ) {
                    Text(
                        tip,
                        color = easyAdbColors.TooltipText,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
