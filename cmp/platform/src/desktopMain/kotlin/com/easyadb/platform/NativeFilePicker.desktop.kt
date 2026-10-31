package com.easyadb.platform

import com.sun.jna.Function
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.platform.win32.Guid
import com.sun.jna.platform.win32.Ole32
import com.sun.jna.platform.win32.W32Errors
import com.sun.jna.platform.win32.WinDef.DWORD
import com.sun.jna.platform.win32.WinDef.DWORDByReference
import com.sun.jna.ptr.PointerByReference
import java.io.File

/**
 * Windows 实现：IFileDialog COM 组件（CLSID_FileOpenDialog）+ FOS_PICKFOLDERS，
 * 与 AWT FileDialog（JDK9+ 同样映射 IFileDialog）完全同源的原生样式——统一
 * 「文件读取/存储/选目录」对话框为系统自带样式（SP-2003 调整2）。
 *
 * JNA 5.14 未内置 IFileDialog 绑定，这里手写 vtable 调用（与 Win32Interop 的
 * 手写 Win32 调用同口径）。COM 初始化/创建失败（极端环境）时回退 Swing
 * JFileChooser，保证功能可用性。
 *
 * 必须在 AWT EDT 调用（模态阻塞至用户选择/取消）。
 */
actual object NativeFilePicker {
    private val CLSID_FileOpenDialog = Guid.GUID("{DC1C5A9C-E88A-4dde-A5A1-60F82A20AEF7}")
    private val IID_IFileDialog = Guid.GUID("{42f51331-5e2f-4b87-b893-7a392a4b9b4c}")
    private val IID_IShellItem = Guid.GUID("{43826d1e-e718-42ee-bc55-a1e261c37bfe}")

    /** FOS_PICKFOLDERS = 0x20（文件夹模式）；FOS_FORCEFILESYSTEM = 0x40（保证返回文件系统路径） */
    private const val FOS_PICKFOLDERS = 0x20L
    private const val FOS_FORCEFILESYSTEM = 0x40L

    /** IFileDialog::Show 用户取消 = ERROR_CANCELLED HRESULT */
    private const val HR_CANCEL = 0x800704C7.toInt()

    /** SIGDN_FILESYSPATH */
    private const val SIGDN_FILESYSPATH = 0x80054000L

    actual fun pickFolder(title: String, initialDir: String?): String? {
        val coInit = Ole32.INSTANCE.CoInitializeEx(null, Ole32.COINIT_APARTMENTTHREADED)
        try {
            val dialogRef = PointerByReference()
            val hr = Ole32.INSTANCE.CoCreateInstance(
                CLSID_FileOpenDialog, null, com.sun.jna.platform.win32.WTypes.CLSCTX_INPROC_SERVER, IID_IFileDialog, dialogRef
            )
            if (W32Errors.FAILED(hr) || dialogRef.value == null) {
                return pickFolderFallback(title)
            }
            val dialog = dialogRef.value
            try {
                val vt = dialog.getPointer(0)
                // IFileDialog vtable：0..2 继承 IUnknown，3=Show，9=SetOptions，10=GetOptions，
                // 12=SetFolder，17=SetTitle，20=GetResult（继承链 IModalWindow→IFileDialog）
                val show = vtMethod(vt, 3)
                val setOptions = vtMethod(vt, 9)
                val getOptions = vtMethod(vt, 10)
                val setFolder = vtMethod(vt, 12)
                val setTitle = vtMethod(vt, 17)
                val getResult = vtMethod(vt, 20)

                // SetOptions(GetOptions() | FOS_PICKFOLDERS | FOS_FORCEFILESYSTEM)
                val opts = DWORDByReference()
                getOptions.invokeVoid(arrayOf(dialog, opts))
                setOptions.invokeVoid(arrayOf(dialog, DWORD(opts.value.toLong() or FOS_PICKFOLDERS or FOS_FORCEFILESYSTEM)))

                setTitle.invokeVoid(arrayOf(dialog, WString(title)))

                // SetFolder：起始目录经 SHCreateItemFromParsingName 解析为 IShellItem
                if (!initialDir.isNullOrBlank()) {
                    val dir = File(initialDir)
                    if (dir.isDirectory) {
                        val itemRef = PointerByReference()
                        val shCreate = Function.getFunction("shell32", "SHCreateItemFromParsingName")
                        if (shCreate.invokeInt(arrayOf(WString(dir.absolutePath), null, IID_IShellItem, itemRef)) == 0 && itemRef.value != null) {
                            setFolder.invokeVoid(arrayOf(dialog, itemRef.value))
                            vtMethod(itemRef.value.getPointer(0), 2).invokeVoid(arrayOf(itemRef.value)) // Release
                        }
                    }
                }

                // Show(owner=null)：模态阻塞直至选择/取消
                val showHr = show.invokeInt(arrayOf(dialog, null as Pointer?))
                if (showHr == HR_CANCEL) return null
                if (showHr != 0) return pickFolderFallback(title)

                // GetResult(&IShellItem) → GetDisplayName(SIGDN_FILESYSPATH)
                val resultRef = PointerByReference()
                if (getResult.invokeInt(arrayOf(dialog, resultRef)) != 0 || resultRef.value == null) {
                    return null
                }
                val item = resultRef.value
                try {
                    val nameRef = PointerByReference()
                    val vtItem = item.getPointer(0)
                    if (vtMethod(vtItem, 5).invokeInt(arrayOf(item, SIGDN_FILESYSPATH, nameRef)) != 0 || nameRef.value == null) {
                        return null
                    }
                    return nameRef.value.getWideString(0)
                } finally {
                    vtMethod(item.getPointer(0), 2).invokeVoid(arrayOf(item)) // Release
                }
            } finally {
                vtMethod(dialog.getPointer(0), 2).invokeVoid(arrayOf(dialog)) // Release
            }
        } finally {
            if (W32Errors.SUCCEEDED(coInit)) {
                Function.getFunction("ole32", "CoUninitialize").invokeVoid(arrayOf())
            }
        }
    }

    private fun vtMethod(vtable: Pointer, index: Int): Function {
        val fnPtr = vtable.getPointer(index * com.sun.jna.Native.POINTER_SIZE.toLong())
        // COM = stdcall，Windows 上 JNA 的 ALT_CONVENTION
        return Function.getFunction(fnPtr, Function.ALT_CONVENTION)
    }

    /** COM 初始化/调用失败兜底：Swing JFileChooser（仅极端环境触达） */
    private fun pickFolderFallback(title: String): String? {
        val chooser = javax.swing.JFileChooser(System.getProperty("user.home"))
        chooser.dialogTitle = title
        chooser.fileSelectionMode = javax.swing.JFileChooser.DIRECTORIES_ONLY
        chooser.isAcceptAllFileFilterUsed = false
        return if (chooser.showOpenDialog(null) == javax.swing.JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile.absolutePath
        } else {
            null
        }
    }
}
