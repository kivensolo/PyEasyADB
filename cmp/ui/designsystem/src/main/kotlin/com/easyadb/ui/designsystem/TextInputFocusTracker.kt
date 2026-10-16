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
 * 所有文本输入框（参数卡 CompactTextField / 文件传输 / 遥控器自定义键码 /
 * 内嵌控制台过滤框）在 onFocusChanged 中调 [onFocusChanged] 维护计数；
 * 弹窗为独立 OS 窗口、有独立键盘管线，不接入也不受接管影响。
 *
 * 接入点新增文本框时须同步维护（漏接的后果：该框聚焦时按键被转投设备）。
 */
object TextInputFocusTracker {
    private var focusedCount by mutableIntStateOf(0)

    /** 当前是否有主窗口内的文本输入框聚焦（true = 键盘接管守卫生效，本地优先） */
    val hasFocusedTextInput: Boolean get() = focusedCount > 0

    /** 文本框 focus 事件回调：聚焦 +1 / 失焦 -1 */
    fun onFocusChanged(focused: Boolean) {
        focusedCount = (focusedCount + if (focused) 1 else -1).coerceAtLeast(0)
    }
}
