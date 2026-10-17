package com.easyadb.ui.dialogs.playground

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.rememberDialogState
import com.easyadb.ui.designsystem.DarkEasyAdbColors
import com.easyadb.ui.designsystem.EasyAdbColors
import com.easyadb.ui.designsystem.EasyAdbTheme
import com.easyadb.ui.designsystem.LightEasyAdbColors
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * 设计 Token 总览弹窗（P12 画廊切片）。
 *
 * 展示 token 体系全貌并支持明暗并排对比：
 * - 明暗并排样张：同一组组件样例分别置于浅色 / 深色主题下（嵌套 [EasyAdbTheme] 实现真并排）；
 * - 圆角四档、间距五档、elevation 三档、描边；
 * - 两套调色板色板格。
 *
 * 注意：DialogWindow 是独立组合根，主窗口的 CompositionLocal 不传播——
 * 内容必须自带 [EasyAdbTheme]（接收画廊的 darkTheme 状态）。这也是全部弹窗
 * 接入主题的标准模式（P12 弹窗翻新统一照此修复）。
 */
@Composable
fun TokenShowcaseDialog(darkTheme: Boolean, onDismiss: () -> Unit) {
    DialogWindow(
        onCloseRequest = onDismiss,
        title = "设计 Token 总览（P12）",
        state = rememberDialogState(width = 880.dp, height = 660.dp)
    ) {
        EasyAdbTheme(darkTheme = darkTheme) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colors.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SectionTitle("明暗并排样张（同一样例 × 两套主题）")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        ThemeSamplePanel("浅色", darkTheme = false, modifier = Modifier.weight(1f))
                        ThemeSamplePanel("深色", darkTheme = true, modifier = Modifier.weight(1f))
                    }

                    SectionTitle("圆角四档（sm 4 · md 6 · lg 8 · xl 12）")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        RadiusSample("sm", easyAdbTokens.shapeSm)
                        RadiusSample("md", easyAdbTokens.shapeMd)
                        RadiusSample("lg", easyAdbTokens.shapeLg)
                        RadiusSample("xl", easyAdbTokens.shapeXl)
                    }

                    SectionTitle("间距五档（4dp 栅格）")
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        SpacingSample("xs", easyAdbTokens.spaceXs)
                        SpacingSample("sm", easyAdbTokens.spaceSm)
                        SpacingSample("md", easyAdbTokens.spaceMd)
                        SpacingSample("lg", easyAdbTokens.spaceLg)
                        SpacingSample("xl", easyAdbTokens.spaceXl)
                    }

                    SectionTitle("elevation 三档（低 2 · 中 4 · 高 8）")
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        ElevationSample("low", easyAdbTokens.elevationLow)
                        ElevationSample("mid", easyAdbTokens.elevationMid)
                        ElevationSample("high", easyAdbTokens.elevationHigh)
                    }

                    SectionTitle("描边（统一 1dp，颜色随主题）")
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(40.dp)
                            .clip(easyAdbTokens.shapeMd)
                            .background(easyAdbColors.Surface)
                            .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, easyAdbTokens.shapeMd),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Border / Divider", fontSize = 11.sp, color = easyAdbColors.TextSecondary)
                    }

                    SectionTitle("调色板 · 浅色 / 深色")
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SwatchGrid(LightEasyAdbColors, modifier = Modifier.weight(1f))
                        SwatchGrid(DarkEasyAdbColors, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.TextPrimary)
}

/** 并排样张：嵌套独立主题，内部组件消费各自的 LocalEasyAdbColors/LocalEasyAdbTokens。 */
@Composable
private fun ThemeSamplePanel(label: String, darkTheme: Boolean, modifier: Modifier = Modifier) {
    EasyAdbTheme(darkTheme = darkTheme) {
        Surface(
            modifier = modifier,
            shape = easyAdbTokens.shapeLg,
            color = easyAdbColors.Surface,
            border = androidx.compose.foundation.BorderStroke(
                easyAdbTokens.strokeWidth, easyAdbColors.Divider
            )
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    label,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = easyAdbColors.TextSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(easyAdbTokens.shapeMd)
                            .background(easyAdbColors.Primary)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text("主按钮", fontSize = 11.sp, color = easyAdbColors.TextOnPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(easyAdbTokens.shapeMd)
                            .background(easyAdbColors.Hover)
                            .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, easyAdbTokens.shapeMd)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text("次按钮", fontSize = 11.sp, color = easyAdbColors.TextPrimary)
                    }
                    Box(
                        modifier = Modifier
                            .clip(easyAdbTokens.shapeMd)
                            .background(easyAdbColors.Selected)
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text("选中项", fontSize = 11.sp, color = easyAdbColors.Primary)
                    }
                }
                Text("正文文字 TextPrimary", fontSize = 12.sp, color = easyAdbColors.TextPrimary)
                Text("次要文字 TextSecondary", fontSize = 12.sp, color = easyAdbColors.TextSecondary)
                Text("提示文字 TextHint", fontSize = 12.sp, color = easyAdbColors.TextHint)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusDot(easyAdbColors.StatusOnline)
                    StatusDot(easyAdbColors.StatusOffline)
                    StatusDot(easyAdbColors.StatusUnauthorized)
                    Text(
                        "日志：",
                        fontSize = 11.sp,
                        color = easyAdbColors.TextSecondary
                    )
                    Text("D", fontSize = 11.sp, color = easyAdbColors.LogDebug)
                    Text("I", fontSize = 11.sp, color = easyAdbColors.LogInfo)
                    Text("W", fontSize = 11.sp, color = easyAdbColors.LogWarn)
                    Text("E", fontSize = 11.sp, color = easyAdbColors.LogError)
                }
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun RadiusSample(label: String, shape: androidx.compose.ui.graphics.Shape) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(width = 76.dp, height = 44.dp)
                .clip(shape)
                .background(easyAdbColors.SurfaceVariant)
                .border(easyAdbTokens.strokeWidth, easyAdbColors.Border, shape)
        )
        Text(label, fontSize = 10.sp, color = easyAdbColors.TextSecondary)
    }
}

@Composable
private fun SpacingSample(label: String, space: androidx.compose.ui.unit.Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row {
            Box(Modifier.size(width = 24.dp, height = 24.dp).clip(easyAdbTokens.shapeSm).background(easyAdbColors.Primary))
            Spacer(Modifier.width(space))
            Box(Modifier.size(width = 24.dp, height = 24.dp).clip(easyAdbTokens.shapeSm).background(easyAdbColors.Primary))
        }
        Text(label, fontSize = 10.sp, color = easyAdbColors.TextSecondary)
    }
}

@Composable
private fun ElevationSample(label: String, elevation: androidx.compose.ui.unit.Dp) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = easyAdbTokens.shapeLg,
            color = easyAdbColors.Surface,
            elevation = elevation,
            modifier = Modifier.size(width = 76.dp, height = 44.dp)
        ) {}
        Text(label, fontSize = 10.sp, color = easyAdbColors.TextSecondary)
    }
}

/** 色板格：一个调色板实例的所有主要色（含取值 hex）。 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwatchGrid(c: EasyAdbColors, modifier: Modifier = Modifier) {
    val swatches = listOf(
        "Primary" to c.Primary, "PrimaryDark" to c.PrimaryDark, "PrimaryLight" to c.PrimaryLight,
        "Surface" to c.Surface, "Background" to c.Background, "SurfaceVariant" to c.SurfaceVariant,
        "PanelHeader" to c.PanelHeader, "TextPrimary" to c.TextPrimary, "TextSecondary" to c.TextSecondary,
        "TextHint" to c.TextHint, "TextDisabled" to c.TextDisabled, "TextOnPrimary" to c.TextOnPrimary,
        "Border" to c.Border, "Divider" to c.Divider, "Hover" to c.Hover, "Selected" to c.Selected,
        "StatusOnline" to c.StatusOnline, "StatusOffline" to c.StatusOffline,
        "StatusUnauthorized" to c.StatusUnauthorized, "LogDebug" to c.LogDebug, "LogInfo" to c.LogInfo,
        "LogWarn" to c.LogWarn, "LogError" to c.LogError, "LogStamp" to c.LogStamp,
        "FindHighlight" to c.FindHighlight, "FindCurrent" to c.FindCurrent,
        "TooltipBackground" to c.TooltipBackground, "TooltipText" to c.TooltipText
    )
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        swatches.forEach { (name, color) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(72.dp)) {
                Box(
                    modifier = Modifier
                        .size(width = 72.dp, height = 26.dp)
                        .clip(easyAdbTokens.shapeSm)
                        .background(color)
                        .border(easyAdbTokens.strokeWidth, easyAdbColors.Divider, easyAdbTokens.shapeSm)
                )
                Text(name, fontSize = 9.sp, color = easyAdbColors.TextSecondary, maxLines = 1)
            }
        }
    }
}
