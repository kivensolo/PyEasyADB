package com.easyadb.ui.filetransfer

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.file.TransferCenter
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * 传输队列（SP-5001，对齐 H5 .queue 走查重设计）——底部卡片列表。
 *
 * 走查反馈「小文件 2~3 秒完成，看不清方向」——重设计：
 * - 方向强展示：左缘 3dp 方向色竖条（出站蓝/入站绿）+ 方向徽章 + 流动箭头动画；
 * - RUNNING 全宽不确定进度条；DONE 绿色「完成」（1.8s 后移除）；FAILED 红字原因；
 * - 队列状态由宿主持有的 [TransferCenter] 推送——切 Tab 面板销毁重建后仍在。
 */
@Composable
fun TransferQueue(transferCenter: TransferCenter, modifier: Modifier = Modifier) {
    val jobs by transferCenter.jobs.collectAsState()
    if (jobs.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        jobs.forEach { job -> QueueCard(job) }
    }
}

@Composable
private fun QueueCard(job: TransferCenter.TransferJob) {
    val out = job.direction == TransferCenter.Direction.PC_TO_DEV
    val dirColor = if (out) easyAdbColors.PrimaryLight else easyAdbColors.StatusOnline
    val barColor = if (out) easyAdbColors.Primary else easyAdbColors.StatusOnline
    Surface(
        shape = easyAdbTokens.shapeMd,
        color = easyAdbColors.Surface,
        elevation = 0.dp,
        modifier = Modifier.fillMaxWidth().drawBehind {
            // 左缘方向色竖条：PC → 设备 蓝 / 设备 → PC 绿
            drawRect(color = barColor, size = androidx.compose.ui.geometry.Size(3.dp.toPx(), size.height))
        }
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.direction.label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = dirColor
                )
                Spacer(Modifier.width(10.dp))
                FlowChevrons(outgoing = out, running = job.state == TransferCenter.State.RUNNING)
                Spacer(Modifier.width(10.dp))
                Text(
                    job.fileName,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = easyAdbColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(10.dp))
                when (job.state) {
                    TransferCenter.State.RUNNING -> Text(
                        "传输中…",
                        fontSize = 11.sp,
                        color = easyAdbColors.TextSecondary
                    )
                    TransferCenter.State.DONE -> Text(
                        "完成",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = easyAdbColors.StatusOnline
                    )
                    TransferCenter.State.FAILED -> Text(
                        job.error ?: "传输失败",
                        fontSize = 11.sp,
                        color = easyAdbColors.LogError,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 260.dp)
                    )
                }
            }
            if (job.state == TransferCenter.State.RUNNING) {
                Spacer(Modifier.height(6.dp))
                // 不确定进度动画（全宽更醒目；真实百分比待 v2 sync 协议）
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = barColor
                )
            }
        }
    }
}

/** 方向流动箭头：三枚箭头依次点亮（RUNNING），终态静止淡化；入站箭头向左 */
@Composable
private fun FlowChevrons(outgoing: Boolean, running: Boolean) {
    val transition = rememberInfiniteTransition(label = "qflow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "qphase"
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { i ->
            val alpha = if (!running) 0.35f else {
                val d = ((phase - i + 3f) % 3f)
                (1f - d / 1.5f).coerceIn(0.2f, 1f)
            }
            Text(
                if (outgoing) "❯" else "❮",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = (if (outgoing) easyAdbColors.PrimaryLight else easyAdbColors.StatusOnline).copy(alpha = alpha)
            )
        }
    }
}
