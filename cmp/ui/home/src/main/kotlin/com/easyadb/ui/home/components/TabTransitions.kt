package com.easyadb.ui.home.components

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/10
 * description：Tab 内容切换过渡动效（对齐 HTML 参照稿）：新面板淡入 + 轻微
 *              上移入场（对齐 .rise 的上移观感，时长取 240ms），旧面板快速
 *              淡出（100ms；H5 的 .pane 为 display 瞬切，此处快速淡出避免
 *              硬切感）。主窗口内容区五 Tab 与内嵌控制台双 Tab 共用此参数。
 */

/** 内容切换入场时长（ms）：淡入 + 上移 */
private const val TAB_ENTER_MS = 240

/** 内容切换退场时长（ms）：快速淡出 */
private const val TAB_EXIT_MS = 100

/**
 * Tab 内容切换的通用 transitionSpec：新内容淡入 + 上移 1/16 高度入场、
 * 旧内容快速淡出。供主窗口内容区五 Tab 与内嵌控制台双 Tab 的
 * [androidx.compose.animation.AnimatedContent] 共用。
 */
internal fun tabSwitchTransition(): AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
    fadeIn(tween(TAB_ENTER_MS, easing = FastOutSlowInEasing)) +
        slideInVertically(tween(TAB_ENTER_MS, easing = FastOutSlowInEasing)) { it / 16 } togetherWith
        fadeOut(tween(TAB_EXIT_MS, easing = FastOutSlowInEasing))
}
