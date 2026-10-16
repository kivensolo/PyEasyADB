package com.easyadb.ui.home.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.Ease
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：Tab 内容切换过渡动效（对齐 HTML 参照稿 .rise）：新面板淡入 +
 *              轻微上移入场（500ms / CSS ease 曲线 / 位移收敛 ~12px 观感，
 *              全量对齐 .rise .5s），旧面板快速淡出（100ms；H5 的 .pane 为
 *              display 瞬切，此处快速淡出避免硬切感）。主窗口内容区五 Tab
 *              与内嵌控制台双 Tab 共用此参数。
 */

/** 内容切换入场时长（ms）：淡入 + 上移，对齐 .rise 的 .5s */
private const val TAB_ENTER_MS = 500

/** 内容切换退场时长（ms）：快速淡出 */
private const val TAB_EXIT_MS = 100

/**
 * Tab 内容切换的通用 transitionSpec：新内容淡入 + 轻微上移入场、旧内容快速淡出。
 * 位移取高度 1/48、上限 16px——H5 为固定 12px，此处随面板高度收敛到同一观感量级
 * （transitionSpec lambda 内无可靠的 Density 环境，不做 dp 换算）；缓动用 [Ease]
 * （= CubicBezier(0.25,0.1,0.25,1)，与 CSS ease 同曲线）。
 * 供主窗口内容区五 Tab 与内嵌控制台双 Tab 的
 * [androidx.compose.animation.AnimatedContent] 共用。
 */
internal fun tabSwitchTransition(): AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
    fadeIn(tween(TAB_ENTER_MS, easing = Ease)) +
        slideInVertically(tween(TAB_ENTER_MS, easing = Ease)) { (it / 48).coerceAtMost(16) } togetherWith
        fadeOut(tween(TAB_EXIT_MS, easing = Ease))
}
