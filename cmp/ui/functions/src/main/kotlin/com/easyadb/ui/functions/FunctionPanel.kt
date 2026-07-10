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
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.config.FunctionItem
import com.easyadb.core.config.FunctionTemplate
import com.easyadb.ui.designsystem.EasyAdbColors

/**
 * 功能面板：组合 [AppParamPanel]（应用参数配置）和 [TemplateGrid]（功能按钮网格）。
 *
 * 对应 Python `src/CenterWindow.py` 中的布局：
 * - 顶部：应用参数配置区域（_initCustomAppActionArea）
 * - 中部：功能模板按钮网格（_setUpUIDynamic）
 *
 * 整个面板支持垂直滚动（对齐 Python 中 CenterWindow 的整体滚动行为）。
 */
@Composable
fun FunctionPanel(
    functionTemplates: List<FunctionTemplate>,
    deviceIp: String?,
    dbPackages: List<String>,
    onPackageAdd: (String) -> Unit,
    onPackageDelete: (String) -> Unit,
    onItemClick: (FunctionItem, AppParamState) -> Unit,
    modifier: Modifier = Modifier
) {
    var appParamState by remember { mutableStateOf(AppParamState()) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
            .verticalScroll(rememberScrollState())
    ) {
        // ── 顶部：应用参数配置 ──
        AppParamPanel(
            dbPackages = dbPackages,
            onPackageAdd = onPackageAdd,
            onPackageDelete = onPackageDelete,
            onStateChanged = { appParamState = it }
        )

        // 分隔线
        Divider(
            color = EasyAdbColors.Divider,
            thickness = 1.dp,
            modifier = Modifier.fillMaxWidth()
        )

        // ── 中部：功能模板网格 ──
        if (functionTemplates.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "加载功能模板中...",
                    fontSize = 14.sp,
                    color = EasyAdbColors.TextSecondary
                )
            }
        } else {
            TemplateGrid(
                templates = functionTemplates,
                deviceIp = deviceIp,
                appParams = appParamState,
                onItemClick = { item -> onItemClick(item, appParamState) }
            )
        }

        // 底部留白
        Box(modifier = Modifier.height(32.dp))
    }
}