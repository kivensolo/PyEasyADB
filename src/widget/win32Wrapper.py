# -*- coding: utf-8 -*-
import os

import win32api
import win32con
import win32gui
import win32process
from PyQt5 import QtWidgets
from PyQt5.QtCore import Qt, QObject, QEvent, QTimer
from PyQt5.QtGui import QWindow, QMouseEvent
from PyQt5.QtWidgets import QSizePolicy, QWidget, QMainWindow

import Dependencies
from src.logcat.log import z_logger
from utils.Tools import getWRYHFontStyle, getSimpleFontStyle
from utils.UITools import UiUtils


class ScrcpyEmbedWidget(QWidget):
    def __init__(self,parent):
        super().__init__(parent)
        self.mainWindow = parent
        self.myhwnd = None
        # scrcpy的原生窗口句柄
        self.scrcpy_hwnd = -1
        # scrcpy的PID
        self.scrcpy_pid = -1
        # 查找scrcpy window窗口的次数
        self.search_conunt = 0
        # scrcpy是否嵌入进QT
        self.isEnableEmbed = True


        # 设置控件可以接收键盘焦点
        self.setFocusPolicy(Qt.StrongFocus)
        self.setContentsMargins(0, 0, 0, 0)
        self.setupUi(self)

        self.timerWaitScrcpy = QTimer()
        self.timerWaitScrcpy.setSingleShot(True)
        self.timerWaitScrcpy.timeout.connect(self.onTimerFinish)
        self.windowTitle = "EasyADB Device Mirror"

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
        # btnsWidgets.setStyleSheet("border: 1px solid green;")
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(btnsWidgets.sizePolicy().hasHeightForWidth())
        btnsWidgets.setSizePolicy(sizePolicy)
        btnsWidgets.setFocusPolicy(Qt.NoFocus)

        buttonsLayout = QtWidgets.QHBoxLayout(btnsWidgets)
        buttonsLayout.setContentsMargins(5, 5, 5, 0)
        self.startScrcpyBtn = QtWidgets.QPushButton(embedWidget)
        self.startScrcpyBtn.setText("开启实时预览")
        self.startScrcpyBtn.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(10)))
        self.startScrcpyBtn.clicked.connect(self.tryConnectScrcpy)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.startScrcpyBtn.sizePolicy().hasHeightForWidth())
        self.startScrcpyBtn.setSizePolicy(sizePolicy)
        self.startScrcpyBtn.setObjectName("startScrcpyBtn")
        buttonsLayout.addWidget(self.startScrcpyBtn, alignment=Qt.AlignLeft)

        self.stopScrcpyBtn = QtWidgets.QPushButton(embedWidget)
        self.stopScrcpyBtn.setText("关闭实时预览")
        self.stopScrcpyBtn.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(10)))
        self.stopScrcpyBtn.setEnabled(False)
        self.stopScrcpyBtn.clicked.connect(self.__killScrcpy)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.stopScrcpyBtn.sizePolicy().hasHeightForWidth())
        self.stopScrcpyBtn.setSizePolicy(sizePolicy)
        self.stopScrcpyBtn.setObjectName("stopScrcpyBtn")
        buttonsLayout.addWidget(self.stopScrcpyBtn, alignment=Qt.AlignLeft)

        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        # sizePolicy.setHeightForWidth(self.tipsLabel.sizePolicy().hasHeightForWidth())

        self.enabneEmbedOption = QtWidgets.QCheckBox()
        self.enabneEmbedOption.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(10)))
        self.enabneEmbedOption.setText("嵌入模式")
        self.enabneEmbedOption.setChecked(self.isEnableEmbed)
        self.enabneEmbedOption.stateChanged.connect(self.embed_state_changed)
        self.enabneEmbedOption.setSizePolicy(sizePolicy)
        buttonsLayout.addWidget(self.enabneEmbedOption, alignment=Qt.AlignLeft)

        self.tipsLabel = QtWidgets.QLabel(embedWidget)
        self.tipsLabel.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(10)))
        self.tipsLabel.setText("(嵌入模式若出现键盘无法控制远程设备的情况,请点击一下此处红色文本.)")
        self.tipsLabel.setStyleSheet("color: #bf200b")
        self.tipsLabel.setSizePolicy(sizePolicy)

        buttonsLayout.addWidget(self.tipsLabel, alignment=Qt.AlignLeft)


        self.verticalLayout.addWidget(btnsWidgets, alignment=Qt.AlignTop)
        self.myhwnd = int(self.winId())

    def embed_state_changed(self, state):
        """
        当复选框状态改变时触发此函数。
        :param state: ，0表示未选中，2表示已选中。
        :return:
        """
        if state == 0:
            self.isEnableEmbed = False
        elif state == 2:
            self.isEnableEmbed = True

    def tryConnectScrcpy(self):
        checker = Dependencies.ScrcpyChecker()
        checker.checkFinished.connect(self.on_check_finished)
        checker.startCheck()

    def on_check_finished(self, checkResult: []):
        result = checkResult[0]
        param = checkResult[1]
        if result:
            if not os.path.exists(param):
                z_logger.error(f"""【WARRING】: 系统找不到指定的文件: {param},
                请从 https://github.com/Genymobile/scrcpy/releases 下载编译好的win64版本。
                例如下载 scrcpy-win64-v2.3.1.zip ，解压缩后修改文件夹名为 scrcpy-win64 放置在tools目录下即可。
                后续如果要升级替换 scrcpy 的版本，只需要替换 scrcpy-win64 目录下的文件即可，实现无缝升级；
                
                【说明】scrcpy 的操作是右键点击返回。
                """)
                return False
            startCMD = f'scrcpy -s {self.mainWindow.current_device_addr} --window-title \"{self.windowTitle}\"'
            self.mainWindow.adbTools.async_exec_adb_cmd([startCMD])
            self.timerWaitScrcpy.start(2000)

        else:
            z_logger.error(f"无法进行远程设备屏幕连接! 说明:\n{param}")
            if 'timeout' in param \
                    or 'Connection aborted' in param:
                z_logger.error("""下载失败！建议开启科学上网后再进行重试!
                也可以从 https://github.com/Genymobile/scrcpy/releases 下载编译好的win64版本。
                例如下载 scrcpy-win64-v2.3.1.zip ，解压缩后修改文件夹名为 scrcpy-win64 放置在'<User>\\AppData\\Local\\EasyADB\\tools'目录下即可。
                后续如果要升级替换 scrcpy 的版本，只需要替换 scrcpy-win64 目录下的文件即可，实现无缝升级；
                """)

    def __killScrcpy(self):
        """
         断开Scrcpy
        :return:
        """
        if self.scrcpy_hwnd == -1:
            z_logger.warn(f"远程设备未连接！")
            return

        if self.isEnableEmbed:
            self.verticalLayout.removeWidget(self.container)

        # 结束Scrcpy进程
        try:
            handle = win32api.OpenProcess(win32con.PROCESS_TERMINATE, False, self.scrcpy_pid)
            win32api.TerminateProcess(handle, 0)
            win32api.CloseHandle(handle)
        except Exception as e:
            z_logger.error(f"关闭实时预览出现异常！\b{e} \n请确认是否单独启动了不同版本的Scrcpy程序!")
        self.scrcpy_hwnd = -1
        self.startScrcpyBtn.setEnabled(True)
        self.stopScrcpyBtn.setEnabled(False)
        self.enabneEmbedOption.setEnabled(True)

    def onTimerFinish(self):
        self.findAndInflateScrcpy()

    def findAndInflateScrcpy(self):
        self.search_conunt += 1
        """
        发现Scrcpy窗口
        :return:
        """
        # win32gui.EnumWindows(self.__enumWindows, None)
        # 根据标题查找窗口句柄
        self.scrcpy_hwnd = win32gui.FindWindow(None, self.windowTitle)
        self.scrcpy_pid = win32process.GetWindowThreadProcessId(self.scrcpy_hwnd)[1]
        if self.scrcpy_hwnd > 0:
            self.search_conunt = 0
            self.startScrcpyBtn.setEnabled(False)
            self.enabneEmbedOption.setEnabled(False)
            self.stopScrcpyBtn.setEnabled(True)

            if self.isEnableEmbed:
                # 找到scrcpy的窗口
                native_window: QWindow = QWindow.fromWinId(self.scrcpy_hwnd)
                # window.setFlags(Qt.WindowType.CustomizeWindowHint)
                # 创建一个QWidget并将QWindow添加到其中

                self.container: QWidget = QWidget.createWindowContainer(native_window)
                self.container.setWindowFlags(Qt.Window | Qt.WindowTitleHint | Qt.WindowMinMaxButtonsHint)
                self.container.setFocusPolicy(Qt.StrongFocus)
                sizePolicy = QSizePolicy(QSizePolicy.Preferred, QSizePolicy.Preferred)
                sizePolicy.setHeightForWidth(self.container.sizePolicy().hasHeightForWidth())
                self.container.setSizePolicy(sizePolicy)
                self.verticalLayout.addWidget(self.container)
                self.verticalLayout.setStretch(1, 2)

            # 设置事件过滤器
            # filter = EventFilter(self, native_window)
            # self.installEventFilter(filter)
        else:
            if self.search_conunt <= 3:
                # 有的设备第一次启动的时候，时间会迟于2秒，减少间隔时间，再次重试(给到5秒的时间)。
                self.timerWaitScrcpy.start(1000)
            else:
                self.search_conunt = 0
                z_logger.error('无法找到有效镜像窗口，请确认远程设备是否已连接！')

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


