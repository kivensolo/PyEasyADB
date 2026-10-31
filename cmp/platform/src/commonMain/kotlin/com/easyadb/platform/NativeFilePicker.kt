package com.easyadb.platform

/**
 * 系统原生文件对话框统一入口（SP-2003 调整：文件读取/存储/选目录统一走系统原生样式）。
 *
 * Windows 实现：
 * - 文件打开/保存 → AWT [java.awt.FileDialog]（JDK 9+ 在 Windows 上映射系统原生
 *   IFileDialog，即「系统自带的那种」），由各调用点直接使用；
 * - 目录选择 → 本入口（IFileDialog COM 组件 + FOS_PICKFOLDERS 文件夹模式，
 *   与文件对话框同原生样式；COM 异常时自动回退 Swing JFileChooser）。
 *
 * 必须在 AWT EDT 调用（对话框阻塞至用户选择/取消）。
 */
expect object NativeFilePicker {
    /** 选择文件夹，返回绝对路径；取消返回 null。[initialDir] 为空或不存在时忽略 */
    fun pickFolder(title: String, initialDir: String?): String?
}
