package com.easyadb.ui.functions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.FunctionItem
import com.easyadb.ui.designsystem.easyAdbColors

/**
 * 功能面板（P12 布局大改）：渲染单个内容 Tab 的功能卡网格。
 *
 * Tab 容器（下划线 Tab 切换）位于 MainWindowScreen；本面板只负责：
 * - 可选的参数卡（快捷操作 Tab 显示：常用命令参数 2×2 网格）；
 * - 功能卡网格（[TemplateGrid]，items 已由调用方按 tab 过滤后平铺；小卡经
 *   [onItemClick]、「安装应用」虚线卡拖入 APK 经 [onApkDropped]、推送/拉取
 *   文件大卡经 [onFileItemClick]（携带设备路径）分发宿主）。
 *
 * 参数状态 [appParamState] 由宿主（MainWindowScreen）持有并传入——快捷操作
 * 功能卡执行与常用命令 Tab 的 {pkg} 模板共享同一份包名。
 *
 * 整个面板支持垂直滚动（功能项较多时）。
 */
@Composable
fun FunctionPanel(
    items: List<FunctionItem>,
    appParamState: AppParamState,
    onParamStateChanged: (AppParamState) -> Unit,
    dbPackages: List<String>,
    onPackageAdd: (String) -> Unit,
    onPackageDelete: (String) -> Unit,
    onItemClick: (FunctionItem, AppParamState) -> Unit,
    onFileItemClick: (FunctionItem, devicePath: String) -> Unit,
    onApkDropped: (String) -> Unit,
    showParamBar: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(easyAdbColors.Surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 可选：常用命令参数卡（仅快捷操作 Tab）──
        if (showParamBar) {
            AppParamPanel(
                dbPackages = dbPackages,
                onPackageAdd = onPackageAdd,
                onPackageDelete = onPackageDelete,
                onStateChanged = onParamStateChanged
            )
        }

        // ── 功能卡网格 ──
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载功能模板中...",
                    fontSize = 14.sp,
                    color = easyAdbColors.TextSecondary
                )
            }
        } else {
            TemplateGrid(
                items = items,
                onItemClick = { item -> onItemClick(item, appParamState) },
                onFileItemClick = onFileItemClick,
                onApkDropped = onApkDropped
            )
        }

        // 底部留白
        Box(modifier = Modifier.height(24.dp))
    }
}
