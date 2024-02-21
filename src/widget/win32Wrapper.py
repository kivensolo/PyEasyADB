# -*- coding: utf-8 -*-
import os

import win32api
import win32con
import win32gui
import win32process
from PyQt5 import QtWidgets
from PyQt5.QtCore import Qt, QObject, QEvent, QTimer
from PyQt5.QtGui import QWindow, QMouseEvent
from PyQt5.QtWidgets import QSizePolicy, QWidget

from src import settings
from src.logcat.log import z_logger


class ScrcpyEmbedWidget(QWidget):
    def __init__(self,parent):
        super().__init__(parent)
        self.mainWindow = parent
        self.myhwnd = None
        # scrcpy的原生窗口句柄
        self.scrcpy_hwnd = -1
        # scrcpy的PID
        self.scrcpy_pid = -1
        # 设置控件可以接收键盘焦点
        self.setFocusPolicy(Qt.StrongFocus)
        self.setContentsMargins(0,0,0,0)
        self.setupUi(self)

        self.timerWaitScrcpy = QTimer()
        self.timerWaitScrcpy.setSingleShot(True)
        self.timerWaitScrcpy.timeout.connect(self.onTimerFinish)
        self.windowTitle = "EasyADB_Scrcpy"

    def setupUi(self, embedWidget):
        embedWidget.setObjectName("embedWidget")
        embedWidget.setEnabled(True)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(embedWidget.sizePolicy().hasHeightForWidth())
        embedWidget.setSizePolicy(sizePolicy)

        self.verticalLayout = QtWidgets.QVBoxLayout(embedWidget)
        self.verticalLayout.setObjectName("verticalLayout")
        self.verticalLayout.setContentsMargins(2, 2, 2, 2)
        # 横向操作按钮
        btnsWidgets = QWidget()
        # btnsWidgets.setStyleSheet("border: 3px solid green;")
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(btnsWidgets.sizePolicy().hasHeightForWidth())
        btnsWidgets.setSizePolicy(sizePolicy)
        btnsWidgets.setFocusPolicy(Qt.NoFocus)

        buttonsLayout = QtWidgets.QHBoxLayout(btnsWidgets)
        buttonsLayout.setContentsMargins(2, 0, 2, 0)
        self.startScrcpyBtn = QtWidgets.QPushButton(embedWidget)
        self.startScrcpyBtn.setText("连接设备屏幕")
        self.startScrcpyBtn.clicked.connect(self.connectScrcpy)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.startScrcpyBtn.sizePolicy().hasHeightForWidth())
        self.startScrcpyBtn.setSizePolicy(sizePolicy)
        self.startScrcpyBtn.setObjectName("startScrcpyBtn")
        buttonsLayout.addWidget(self.startScrcpyBtn, alignment=Qt.AlignLeft)

        self.stopScrcpyBtn = QtWidgets.QPushButton(embedWidget)
        self.stopScrcpyBtn.setText("断开设备屏幕")
        self.stopScrcpyBtn.setEnabled(False)
        self.stopScrcpyBtn.clicked.connect(self.__killScrcpy)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.stopScrcpyBtn.sizePolicy().hasHeightForWidth())
        self.stopScrcpyBtn.setSizePolicy(sizePolicy)
        self.stopScrcpyBtn.setObjectName("stopScrcpyBtn")
        buttonsLayout.addWidget(self.stopScrcpyBtn, alignment=Qt.AlignLeft)

        self.verticalLayout.addWidget(btnsWidgets, alignment=Qt.AlignTop)
        self.myhwnd = int(self.winId())

    def connectScrcpy(self):
        """
        连接Scrcpy
        :return:
        """
        scrcpyExe = f'{settings.SCRCPY_PATH}\\scrcpy.exe'
        if not os.path.exists(scrcpyExe):
            z_logger.error(f"""【WARRING】: 系统找不到指定的文件: {scrcpyExe},
            请从 https://github.com/Genymobile/scrcpy/releases 下载编译好的win64版本。
            例如下载 scrcpy-win64-v2.3.1.zip ，解压缩后修改文件夹名为 scrcpy-win64 放置在tool目录下即可。
            如果要升级替换 scrcpy 的版本，只需要替换 scrcpy-win64 目录下的文件即可，实现无缝升级；
            
            【说明】scrcpy 的操作是右键点击返回。
            """)
            return False
        startCMD = f'{scrcpyExe} --window-title {self.windowTitle}'
        self.mainWindow.adbTools.async_exec_adb_cmd([startCMD])
        self.timerWaitScrcpy.start(2000)

    def __killScrcpy(self):
        """
         断开Scrcpy
        :return:
        """
        if self.scrcpy_hwnd == -1:
            z_logger.warn(f"远程设备未连接！")
            return
        self.verticalLayout.removeWidget(self.container)

        # 结束Scrcpy进程
        handle = win32api.OpenProcess(win32con.PROCESS_TERMINATE, False, self.scrcpy_pid)
        win32api.TerminateProcess(handle, 0)
        win32api.CloseHandle(handle)
        self.scrcpy_hwnd = -1
        self.startScrcpyBtn.setEnabled(True)
        self.stopScrcpyBtn.setEnabled(False)

    def onTimerFinish(self):
        self.findAndInflateScrcpy()

    def findAndInflateScrcpy(self):
        """
        发现Scrcpy窗口
        :return:
        """
        # win32gui.EnumWindows(self.__enumWindows, None)
        # 根据标题查找窗口句柄
        self.scrcpy_hwnd = win32gui.FindWindow(None, self.windowTitle)
        self.scrcpy_pid = win32process.GetWindowThreadProcessId(self.scrcpy_hwnd)[1]
        if self.scrcpy_hwnd != -1:
            self.startScrcpyBtn.setEnabled(False)
            self.stopScrcpyBtn.setEnabled(True)
            # 找到scrcpy的窗口
            native_window: QWindow = QWindow.fromWinId(self.scrcpy_hwnd)
            # window.setFlags(Qt.WindowType.CustomizeWindowHint)
            # 创建一个QWidget并将QWindow添加到其中

            self.container: QWidget = QWidget.createWindowContainer(native_window)
            self.container.setWindowFlags(Qt.Window | Qt.WindowTitleHint | Qt.WindowMinMaxButtonsHint)
            self.container.setWindowTitle("测试数据")
            self.container.setFocusPolicy(Qt.StrongFocus)
            # 保留原始窗口的边框和标题栏, 直接使用 QWindow 对象，并将其嵌入到一个 QWidget
            # embedded_window = EmbeddedWindow(_hwnd)
            sizePolicy = QSizePolicy(QSizePolicy.Preferred, QSizePolicy.Preferred)
            sizePolicy.setHeightForWidth(self.container.sizePolicy().hasHeightForWidth())
            self.container.setSizePolicy(sizePolicy)
            self.verticalLayout.addWidget(self.container)
            self.verticalLayout.setStretch(1, 2)

            # 设置事件过滤器
            # filter = EventFilter(self, native_window)
            # self.installEventFilter(filter)
        else:
            z_logger.error('请确认远程设备已连接！！！')

    def __enumWindows(self, hwnd, what):
        """
        遍历查找scrcpy的window窗口
        """
        if hwnd == self.myhwnd:
            return  # 过滤自己

        if win32gui.IsWindow(hwnd) and win32gui.IsWindowVisible(hwnd) and win32gui.IsWindowEnabled(hwnd):
            phwnd = win32gui.GetParent(hwnd)
            title = win32gui.GetWindowText(hwnd)
            name = win32gui.GetClassName(hwnd)
            _info = f'{hwnd:<10}|{phwnd:<10}|标题：{title:<10}|类名：{name:<10}'
            if name == "SDL_app":
                print(f"Scrcpy的信息为:{_info}")
                self.scrcpy_hwnd = hwnd
                self.scrcpy_pid = win32process.GetWindowThreadProcessId(hwnd)[1]

    def mousePressEvent(self, event: QMouseEvent):
        if self.scrcpy_hwnd == -1:
            return super().mousePressEvent(event)
        if event.button() == Qt.LeftButton:
            win32gui.SetFocus(self.scrcpy_hwnd)
        elif event.button() == Qt.RightButton:
            print("右键点击")
        elif event.button() == Qt.MiddleButton:
            print("中键点击")

class EventFilter(QObject):
    """
    事件过滤，目的用于转发按键（但是有bug,无法转发Enter键值）
    """
    def __init__(self, parent, native_window):
        super().__init__(parent)
        self.native_window = native_window

    def eventFilter(self, obj, event):
        if event.type() == QEvent.KeyPress:
            # 获取虚拟键码（Virtual Key Code）
            virtual_key_code = event.nativeVirtualKey()
            # 对于其他非字符键，继续发送 WM_KEYDOWN 消息
            scan_code = event.nativeScanCode()
            extended = 0 if (event.modifiers() & Qt.ShiftModifier) else 1
            # 构造lparam，这里简化处理，仅包含扫描码
            lparam = (scan_code << 16) | (extended << 24)

            win32api.SendMessage(self.native_window.winId(), win32con.WM_KEYDOWN, virtual_key_code, lparam)
            return True
            # 消耗事件，防止Qt进一步处理
        return super().eventFilter(obj, event)


