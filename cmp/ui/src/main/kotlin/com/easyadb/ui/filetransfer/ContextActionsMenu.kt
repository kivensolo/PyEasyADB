@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)

package com.easyadb.ui.filetransfer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalDensity
import com.easyadb.ui.designsystem.AppIcons
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 设备树右键菜单（SP-5001 走查补强）：样式逐项对齐 H5 参照稿 .ctx-* 系列——
 * panel 底 + Border 描边 + 12dp 圆角浮层（H5 .ctx-menu border-radius:12px）、
 * 菜单项 hover 蓝底字色翻转（图标随字色，H5 .ctx-item:hover）、分组横线（.ctx-sep）、
 * 「新建」二级子菜单紧贴主菜单右缘（.ctx-sub，4dp 重叠防 hover 穿隙）。
 * 子菜单为同窗口锚定浮层——CMP 1.7.3 Popup 嵌套 Popup 会退化（内层按父窗口
 * 尺寸测量撑满且遮蔽 hover）；入场 scale+淡入对齐 H5 ctx-in keyframes。
 */

/** 菜单项动作：label + 图标（视觉与工具栏动作一一对应） */
data class MenuAction(
    val label: String,
    val icon: ImageVector,
    val enabled: Boolean = true,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/** 「新建」二级子菜单动作（文件/文件夹），icon 为子项自身图标 */
data class SubMenuAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

/** 菜单内容模型：普通项 / 分组分隔线 / 带子菜单项——按声明顺序渲染 */
sealed class MenuItemSpec {
    data class Action(val action: MenuAction) : MenuItemSpec()
    data object Separator : MenuItemSpec()
    data class Submenu(val action: MenuAction, val children: List<SubMenuAction>) : MenuItemSpec()
}

/** 菜单项行圆角（H5 .ctx-item border-radius:7px，介于 shapeMd/shapeLg 之间） */
private val MenuRowShape = RoundedCornerShape(7.dp)

/**
 * 右键菜单主体。[items] 按声明顺序渲染，分组间插 [MenuItemSpec.Separator]；
 * 唯一可展开的 Submenu 行锚定子菜单浮层（同窗口，Layout 包围盒撑出 Popup 尺寸）。
 * 由 FileTreePane 的容器级 Popup 锚定弹出，本组件只画内容。
 */
@Composable
fun ContextActionsMenu(
    items: List<MenuItemSpec>,
    onDismiss: () -> Unit
) {
    var expandedIndex by remember { mutableStateOf<Int?>(null) }
    // 「新建」行顶相对菜单根的纵向偏移（px，同窗口坐标差）
    var submenuTopPx by remember { mutableStateOf(0f) }
    var menuRootWindowY by remember { mutableStateOf(0f) }
    val density = LocalDensity.current

    // 入场动画（H5 ctx-in：scale .96→1 + 淡入，120ms）
    var shown by remember { mutableStateOf(false) }
    val anim by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = tween(durationMillis = 120),
        label = "ctxMenuIn"
    )
    LaunchedEffect(Unit) { shown = true }

    val submenuSpec = (items.getOrNull(expandedIndex ?: -1) as? MenuItemSpec.Submenu)

    // 指针移出菜单窗口整体（主菜单 + 子菜单包围盒）即收起子菜单
    val collapseOnExit = Modifier.onPointerEvent(PointerEventType.Exit) { expandedIndex = null }

    Layout(
        modifier = collapseOnExit
            .alpha(anim)
            .scale(0.96f + 0.04f * anim),
        content = {
            Surface(
                shape = easyAdbTokens.shapeXl,
                color = easyAdbColors.Surface,
                border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Border),
                elevation = easyAdbTokens.elevationHigh,
                modifier = Modifier
                    .width(IntrinsicSize.Min)
                    .onGloballyPositioned { menuRootWindowY = it.positionInWindow().y }
            ) {
                Column(modifier = Modifier.padding(vertical = 5.dp)) {
                    items.forEachIndexed { index, spec ->
                        when (spec) {
                            is MenuItemSpec.Action -> MenuRow(
                                label = spec.action.label,
                                icon = spec.action.icon,
                                enabled = spec.action.enabled,
                                danger = spec.action.danger,
                                onHoverEnter = { expandedIndex = null },
                                onClick = {
                                    onDismiss()
                                    spec.action.onClick()
                                }
                            )
                            is MenuItemSpec.Separator -> MenuSeparator()
                            is MenuItemSpec.Submenu -> MenuRow(
                                label = spec.action.label,
                                icon = spec.action.icon,
                                enabled = spec.action.enabled,
                                showSubmenuArrow = true,
                                onHoverEnter = { expandedIndex = index },
                                onClick = { expandedIndex = index },
                                modifier = Modifier.onGloballyPositioned { coords ->
                                    submenuTopPx = coords.positionInWindow().y - menuRootWindowY
                                }
                            )
                        }
                    }
                }
            }
            if (submenuSpec != null) {
                // 子菜单：H5 .ctx-sub——panel 底 + 描边 + 12dp 圆角
                Surface(
                    shape = easyAdbTokens.shapeXl,
                    color = easyAdbColors.Surface,
                    border = BorderStroke(easyAdbTokens.strokeWidth, easyAdbColors.Border),
                    elevation = easyAdbTokens.elevationHigh
                ) {
                    Column(modifier = Modifier.padding(vertical = 5.dp)) {
                        submenuSpec.children.forEach { child ->
                            MenuRow(
                                label = child.label,
                                icon = child.icon,
                                onClick = {
                                    onDismiss()
                                    child.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { measurables, constraints ->
        val menuPlaceable = measurables[0].measure(constraints)
        val subPlaceable = measurables.getOrNull(1)?.measure(Constraints())
        val subW = subPlaceable?.width ?: 0
        val subH = subPlaceable?.height ?: 0
        // 子菜单紧贴主菜单右缘（-4dp 重叠防穿隙，H5 calc(100% - 4px)），
        // 纵向对齐「新建」行顶上移 6dp（H5 top:-6px）、不越出菜单底缘
        val overlap4 = with(density) { 4.dp.roundToPx() }
        val lift6 = with(density) { 6.dp.roundToPx() }
        val x = menuPlaceable.width - overlap4
        val y = (submenuTopPx.roundToInt() - lift6)
            .coerceIn(0, (menuPlaceable.height - subH).coerceAtLeast(0))
        layout(max(menuPlaceable.width, x + subW), max(menuPlaceable.height, y + subH)) {
            menuPlaceable.place(0, 0)
            subPlaceable?.placeRelative(x, y)
        }
    }
}

/** 水平分组分隔线（H5 .ctx-sep：上下 4dp、左右 8dp 留白 1px 细线） */
@Composable
private fun MenuSeparator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(1.dp)
            .background(easyAdbColors.Divider)
    )
}

/** 带图标的菜单行：hover 蓝底 + 字色/图标翻转，禁用置灰不可点，危险项红系（H5 .ctx-item） */
@Composable
private fun MenuRow(
    label: String,
    icon: ImageVector,
    enabled: Boolean = true,
    danger: Boolean = false,
    showSubmenuArrow: Boolean = false,
    modifier: Modifier = Modifier,
    onHoverEnter: (() -> Unit)? = null,
    onClick: () -> Unit
) {
    var hovered by remember { mutableStateOf(false) }
    val active = hovered && enabled
    val c = easyAdbColors
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(30.dp)
            .clip(MenuRowShape)
            .background(
                when {
                    active && danger -> c.AccentRed.copy(alpha = 0.16f)
                    active -> c.MenuHoverBg
                    else -> Color.Transparent
                }
            )
            .then(
                if (enabled) {
                    Modifier.clickable(interactionSource = null, indication = null, onClick = onClick)
                } else Modifier
            )
            .onPointerEvent(PointerEventType.Enter) {
                hovered = true
                onHoverEnter?.invoke()
            }
            .onPointerEvent(PointerEventType.Exit) { hovered = false }
            .padding(horizontal = 11.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = when {
                    !enabled -> c.TextDisabled.copy(alpha = 0.6f)
                    active -> c.MenuHoverText
                    danger -> c.AccentRed
                    else -> c.TextSecondary
                },
                modifier = Modifier.size(14.dp)
            )
            Spacer(Modifier.width(9.dp))
            Text(
                label,
                fontSize = 12.5.sp,
                color = when {
                    !enabled -> c.TextDisabled.copy(alpha = 0.7f)
                    active -> c.MenuHoverText
                    danger -> c.AccentRed
                    else -> c.TextPrimary
                }
            )
        }
        if (showSubmenuArrow) {
            Icon(
                imageVector = AppIcons.ArrowRight,
                contentDescription = null,
                tint = if (active) c.MenuHoverText else c.TextHint,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .size(11.dp)
            )
        }
    }
}
