package com.easyadb.ui.filetransfer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.easyadb.core.file.TransferCenter
import com.easyadb.ui.designsystem.easyAdbColors
import com.easyadb.ui.designsystem.easyAdbTokens

/**
 * 传输队列（SP-5001，对齐 H5 .queue）——底部卡片列表。
 *
 * v1 进度口径：RUNNING = 不确定进度动画；DONE = 绿色「完成」（1.2s 后由
 * TransferCenter 移除）；FAILED = 红字错误摘要（保留至下次任务查看）。
 * 队列状态由宿主持有的 [TransferCenter] 推送——切 Tab 面板销毁重建后仍在。
 */
@Composable
fun TransferQueue(transferCenter: TransferCenter, modifier: Modifier = Modifier) {
    val jobs by transferCenter.jobs.collectAsState()
    if (jobs.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        jobs.forEach { job ->
            Surface(
                shape = easyAdbTokens.shapeMd,
                color = easyAdbColors.Surface,
                elevation = 0.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        job.direction.label,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = easyAdbColors.PrimaryLight
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        job.fileName,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = easyAdbColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(12.dp))
                    when (job.state) {
                        TransferCenter.State.RUNNING -> {
                            CircularProgressIndicator(
                                strokeWidth = 1.8.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text("传输中…", fontSize = 11.sp, color = easyAdbColors.TextSecondary)
                        }
                        TransferCenter.State.DONE -> {
                            Text("完成", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = easyAdbColors.StatusOnline)
                        }
                        TransferCenter.State.FAILED -> {
                            Text(
                                "失败 ${job.error ?: ""}",
                                fontSize = 11.sp,
                                color = easyAdbColors.LogError,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.width(220.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
