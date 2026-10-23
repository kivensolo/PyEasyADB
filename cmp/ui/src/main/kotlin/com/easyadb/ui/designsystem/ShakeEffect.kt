package com.easyadb.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWang
 * date：2026/10/23
 * description：输入框错误抖动效果（对齐 H5 参照稿 @keyframes shake 与 .pfield.shake）。
 *              外部调用 [FieldShakeState.shake] 触发一次 500ms 抖动：水平位移 ±5dp
 *              （15%/45%/75% 左移、30%/60%/90% 右移）+ 红色背景闪烁（16%/7% 交替），
 *              红边保持 560ms 后自动恢复（H5 定时移除 .shake 类的口径），可重复触发
 *              重放动画。视觉消费方式：输入框组件内调用 [FieldShakeState.observeFrame]
 *              取每帧位移/叠色/红边状态自行绘制，触发时可顺带聚焦输入框
 *              （对齐 H5 shakeField 的 inp.focus()）。
 */

/** H5 @keyframes shake 总时长（ms）。 */
private const val SHAKE_DURATION_MS = 500

/** H5 setTimeout 560ms 移除 .shake 类：红边保持窗口。 */
private const val SHAKE_EDGE_HOLD_MS = 560

/** 位移幅值（H5 translateX ±5px，1x 缩放下 1px ≈ 1dp）。 */
private const val SHAKE_AMPLITUDE_DP = 5

/** H5 背景闪烁峰值：rgba(248,113,113,.16) / .07 两档交替。 */
private const val SHAKE_BG_STRONG = 0.16f
private const val SHAKE_BG_WEAK = 0.07f

/**
 * 单个输入框的抖动触发状态：[shake] 每调用一次 [trigger] 自增，
 * 消费端以 trigger 为监听键重放动画（等价 H5 remove/add .shake 类强制重启）。
 */
@Stable
class FieldShakeState {
    /** 抖动请求序号：0 = 尚未触发。 */
    var trigger by mutableIntStateOf(0)
        private set

    /** 触发一次抖动（缺参校验失败时由宿主调用）。 */
    fun shake() {
        trigger++
    }
}

/** 抖动帧：某时刻的水平位移(px)、红色叠加透明度与是否处于红边保持窗口。 */
@Immutable
class ShakeFrame internal constructor(
    val translationXPx: Float,
    val redOverlayAlpha: Float,
    val redEdge: Boolean
)

/**
 * 订阅并驱动抖动动画，返回当前帧（动画期间每帧返回新值）。在输入框组件体内调用一次。
 *
 * @param focusRequester 可选——传入且已绑定到文本节点时，触发瞬间聚焦该输入框
 * @param density 位移幅值 dp→px 换算
 */
@Composable
fun FieldShakeState.observeFrame(
    focusRequester: FocusRequester? = null,
    density: Density = LocalDensity.current
): ShakeFrame {
    val translationX = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(0f) }
    var redEdge by remember { mutableStateOf(false) }

    LaunchedEffect(trigger) {
        if (trigger == 0) return@LaunchedEffect
        val amplitudePx = with(density) { SHAKE_AMPLITUDE_DP.dp.toPx() }
        redEdge = true
        // H5 shakeField 会同时 focus 输入框；requester 尚未挂载到节点时聚焦会抛异常，防御即可
        if (focusRequester != null) {
            runCatching { focusRequester.requestFocus() }
        }
        coroutineScope {
            launch {
                translationX.snapTo(0f)
                translationX.animateTo(0f, offsetKeyframes(amplitudePx))
            }
            launch {
                overlayAlpha.snapTo(0f)
                overlayAlpha.animateTo(0f, overlayKeyframes())
            }
        }
        delay((SHAKE_EDGE_HOLD_MS - SHAKE_DURATION_MS).toLong())
        redEdge = false
    }
    return ShakeFrame(translationX.value, overlayAlpha.value, redEdge)
}

/** H5 @keyframes shake 位移轨：0%→0，15%/45%/75%→-5px，30%/60%/90%→+5px，100%→0。 */
private fun offsetKeyframes(amplitudePx: Float) = keyframes<Float> {
    durationMillis = SHAKE_DURATION_MS
    0f at 0
    -amplitudePx at 75
    amplitudePx at 150
    -amplitudePx at 225
    amplitudePx at 300
    -amplitudePx at 375
    amplitudePx at 450
    0f at SHAKE_DURATION_MS
}

/** H5 @keyframes shake 背景轨：红色叠加 16%/7% 两档交替，首尾透明。 */
private fun overlayKeyframes() = keyframes<Float> {
    durationMillis = SHAKE_DURATION_MS
    0f at 0
    SHAKE_BG_STRONG at 75
    SHAKE_BG_WEAK at 150
    SHAKE_BG_STRONG at 225
    SHAKE_BG_WEAK at 300
    SHAKE_BG_STRONG at 375
    SHAKE_BG_WEAK at 450
    0f at SHAKE_DURATION_MS
}
