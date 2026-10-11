package com.easyadb.ui.home.components

import androidx.compose.runtime.Composable

/**
 * Copyright (c) 2026, 北京视达科科技有限责任公司 All rights reserved.
 * author：ZekeWong
 * date：2026/10/11
 * description：无装饰窗口（undecorated）的窗口控制能力集合。窗口原生标题栏去除后，
 *              最小化/最大化/关闭与拖拽移动全部由应用头栏承担；UI 模块不直接依赖
 *              Compose Window API，由 desktop-app 宿主经本类注入实现（WindowDraggableArea
 *              是 WindowScope 的扩展函数，只能宿主层提供），头栏据此渲染窗口按钮与拖拽区。
 */
data class WindowControls(
    /** 头栏拖拽包装 slot：宿主用 WindowDraggableArea 包裹内容，实现整条头栏拖动窗口 */
    val dragArea: @Composable (content: @Composable () -> Unit) -> Unit,
    /** 最小化窗口（任务栏可恢复） */
    val minimize: () -> Unit,
    /** 最大化 / 还原切换（对齐原生标题栏双击行为） */
    val toggleMaximize: () -> Unit,
    /** 关闭窗口（宿主先做资源清理：watcher / 数据库 / 日志） */
    val close: () -> Unit
)
