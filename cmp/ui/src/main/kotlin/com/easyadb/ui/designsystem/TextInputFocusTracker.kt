package com.easyadb.ui.designsystem

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/16
 * description：主窗口内文本输入框的全局聚焦计数器。
 *
 * 用途：实时预览 Tab 激活期间的窗口级键盘接管（转投 scrcpy）需要"有文本框
 * 正在聚焦"作守卫——聚焦时按键本地优先（打字不能被劫持到设备）。主窗口内
 * 所有文本输入框（参数卡 CompactTextField / 遥控器自定义键码 / 内嵌控制台
 * 过滤框、日志过滤/查找框）在 onFocusChanged 中调 [onFocusChanged] 维护计数。
 *
 * 接入约定（2026-10-27 收紧）：
 * - **只允许主窗口内的文本框接入**——浮层弹窗（卸载确认等主窗口内弹层）的字段
 *   级上报与弹窗级兜底 +1 是双重计数，一律去掉，只保留弹窗级 DisposableEffect
 *   兜底一处；独立 DialogWindow（另存/文本输入/录制/截图等）为独立 OS 窗口、
 *   独立键盘管线，**不接入也不受接管影响**（接入是纯泄漏：窗口销毁时若字段持焦，
 *   Compose 不派发失焦事件，计数永久卡 >0，接管守卫恒 true，键盘永久失联）。
 * - 新增主窗口文本框时须同步维护（漏接的后果：该框聚焦时按键被转投设备）。
 */
object TextInputFocusTracker {
    private var focusedCount by mutableIntStateOf(0)

    /** 当前是否有主窗口内的文本输入框聚焦（true = 键盘接管守卫生效，本地优先） */
    val hasFocusedTextInput: Boolean get() = focusedCount > 0

    /** 文本框 focus 事件回调：聚焦 +1 / 失焦 -1 */
    fun onFocusChanged(focused: Boolean) {
        focusedCount = (focusedCount + if (focused) 1 else -1).coerceAtLeast(0)
    }

    /** 诊断用：当前计数值（键盘失联排查时对照——预览运行中该值必须为 0） */
    val focusedCountForDebug: Int get() = focusedCount
}
