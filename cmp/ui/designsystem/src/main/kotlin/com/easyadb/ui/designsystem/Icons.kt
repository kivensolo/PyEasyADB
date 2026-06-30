package com.easyadb.ui.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * 应用图标引用。
 * 将 Python res/icons 目录的 PNG 文件映射到 Material Icons 等效图标。
 *
 * 备注：
 * - root_32x32.png / unroot_32x32.png → 无 Material 等效（后续使用自定义图形）
 * - apk_64x64_09a413.png → Icons.Filled.Build
 * - open_shell_32x32.png → Icons.Filled.Terminal
 */
object AppIcons {
    val AddNew: ImageVector get() = Icons.Filled.Add
    val Close: ImageVector get() = Icons.Filled.Close
    val Clear: ImageVector get() = Icons.Filled.Delete
    val Edit: ImageVector get() = Icons.Filled.Edit
    val Help: ImageVector get() = Icons.AutoMirrored.Filled.Help
    val Info: ImageVector get() = Icons.Filled.Info
    val Refresh: ImageVector get() = Icons.Filled.Refresh
    val Search: ImageVector get() = Icons.Filled.Search
    val Settings: ImageVector get() = Icons.Filled.Settings
    val Terminal: ImageVector get() = Icons.Filled.Terminal
    val Android: ImageVector get() = Icons.Filled.PhoneAndroid
    val Build: ImageVector get() = Icons.Filled.Build
    val Play: ImageVector get() = Icons.Filled.PlayArrow
    val Stop: ImageVector get() = Icons.Filled.Stop
    val ArrowUp: ImageVector get() = Icons.Filled.KeyboardArrowUp
    val ArrowDown: ImageVector get() = Icons.Filled.KeyboardArrowDown
    val ArrowLeft: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowLeft
    val ArrowRight: ImageVector get() = Icons.AutoMirrored.Filled.KeyboardArrowRight
}
