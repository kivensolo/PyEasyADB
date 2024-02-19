import subprocess
import sys
import time

import win32con
import win32gui
from PyQt5 import QtWidgets
from PyQt5.QtCore import Qt, QSize
from PyQt5.QtGui import QWindow
from PyQt5.QtWidgets import QApplication, QMainWindow, QVBoxLayout, QWidget, QPushButton, QLabel, QListWidget, \
    QSizePolicy

"""
https://github.com/kuoted/Quotes/blob/2f14180b391efb5dc3c7b7f256f16afeb6072b35/EmbedWidget.py#L54

"""
def get_window_handle(window_title):
    # 获取窗口句柄 https://learn.microsoft.com/zh-cn/troubleshoot/windows-server/performance/obtain-console-window-handle
    hwnd = win32gui.FindWindow(None, window_title)
    if hwnd == 0:
        raise Exception(f"Window not found: {window_title}")
    return hwnd


class MainWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.resize(703, 569)

        self.central_widget = QWidget()
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.central_widget.sizePolicy().hasHeightForWidth())
        self.central_widget.setSizePolicy(sizePolicy)
        self.setCentralWidget(self.central_widget)

        self.verticalLayout = QVBoxLayout(self.central_widget)
        self.showBtn = QPushButton(self.central_widget)
        self.showBtn.setText("获取所有可用、可视窗口")
        self.showBtn.clicked.connect(self._getWindowList)
        sizePolicy = QSizePolicy(QSizePolicy.Minimum, QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.showBtn.sizePolicy().hasHeightForWidth())
        self.showBtn.setSizePolicy(sizePolicy)
        self.verticalLayout.addWidget(self.showBtn)

        self.tips = QLabel('双击列表中的项目则进行嵌入目标窗口到下方\n格式为：句柄|父句柄|标题|类名', self)
        # btn.clicked.connect(self.onClick)
        sizePolicy1 = QSizePolicy(QSizePolicy.Preferred, QSizePolicy.Fixed)
        sizePolicy1.setHorizontalStretch(0)
        sizePolicy1.setVerticalStretch(0)
        sizePolicy1.setHeightForWidth(self.tips.sizePolicy().hasHeightForWidth())
        self.tips.setSizePolicy(sizePolicy1)
        self.verticalLayout.addWidget(self.tips)

        self.windowList = QListWidget(self, itemDoubleClicked=self.onItemDoubleClicked)
        sizePolicy2 = QSizePolicy(QSizePolicy.Expanding, QSizePolicy.Preferred)
        sizePolicy2.setHorizontalStretch(0)
        sizePolicy2.setVerticalStretch(0)
        sizePolicy2.setHeightForWidth(self.windowList.sizePolicy().hasHeightForWidth())
        self.windowList.setSizePolicy(sizePolicy2)
        self.verticalLayout.addWidget(self.windowList)
        self.verticalLayout.setStretch(2, 1)

        # 自己的句柄
        self.myhwnd = int(self.winId())

    # def onClick(self):
    #     # 启动记事本
    #     # self.process = subprocess.Popen(['C:\\Users\\ZekeWang\\Downloads\\scrcpy-win64-v2.3.1\\scrcpy-win64-v2.3.1\\scrcpy.exe'])
    #     self.process = subprocess.Popen(['msinfo32.exe'])
    #     # 等待记事本启动
    #     time.sleep(2)
    #     # # 获取前置窗口句柄
    #     hwnd = win32gui.GetForegroundWindow()
    #     # 创建一个win32窗口的代理
    #     embed_window: QWindow = QWindow.fromWinId(hwnd)
    #     embed_window.setTitle("XXXXXXXXX")
    #     self.container: QWidget = QWidget.createWindowContainer(embed_window)
    #     self.container.setMinimumHeight(500)
    #     self.container.setWindowFlags(Qt.Window)
    #     # self.container.setParent(self)
    #     self.verticalLayout.addWidget(self.container)
    #     self.verticalLayout.setStretch(0, 0)
    #     self.verticalLayout.setStretch(1, 0)
    #     self.verticalLayout.setStretch(2, 5)
    #     self.verticalLayout.setStretch(3, 8)

    def onItemDoubleClicked(self, item):
        """列表双击选择事件"""

        self.windowList.takeItem(self.windowList.indexFromItem(item).row())
        hwnd, phwnd, _, _ = item.text().split('|')

        # 先移除掉item
        if self.verticalLayout.count() == 4:
            # 如果数量等于4说明之前已经嵌入了一个窗口，现在需要把它释放出来
            self.verticalLayout.removeWidget(self.embedded_window)

        # 检索窗口的 32 位值
        _hwnd, _phwnd = int(hwnd), int(phwnd)
        # 将原始窗口句柄转换为一个 QWindow 对象（即win32窗口的代理）
        embed_window: QWindow = QWindow.fromWinId(_hwnd)
        # embed_window.setWindowFlags(Qt.Window)
        # createWindowContainer() 创建的是一个不包含边框和标题栏的窗口容器。
        self.embedded_window = QWidget.createWindowContainer(embed_window)

        # 保留原始窗口的边框和标题栏, 直接使用 QWindow 对象，并将其嵌入到一个 QWidget
        # embedded_window = EmbeddedWindow(_hwnd)
        sizePolicy = QSizePolicy(QSizePolicy.Preferred, QSizePolicy.Preferred)
        sizePolicy.setHeightForWidth(self.embedded_window.sizePolicy().hasHeightForWidth())
        self.embedded_window.setSizePolicy(sizePolicy)

        self.verticalLayout.addWidget(self.embedded_window)
        self.verticalLayout.setStretch(3, 2)


    def _getWindowList(self):
        """清空原来的列表"""
        self.windowList.clear()
        win32gui.EnumWindows(self._enumWindows, None)

    def _enumWindows(self, hwnd, what):
        """遍历回调函数"""
        if hwnd == self.myhwnd:
            return  # 防止自己嵌入自己
        if win32gui.IsWindow(hwnd) and win32gui.IsWindowVisible(hwnd) and win32gui.IsWindowEnabled(hwnd):
            phwnd = win32gui.GetParent(hwnd)
            title = win32gui.GetWindowText(hwnd)
            name = win32gui.GetClassName(hwnd)

            _info = f'{hwnd:<10}|{phwnd:<10}|标题：{title:<10}|类名：{name:<10}'
            self.windowList.addItem(_info)
            if name == "SDL_app":
                print(f"Scrcpy的信息为:{_info}")


class EmbeddedWindow(QWidget):
    def __init__(self, hwnd, parent=None):
        super().__init__(parent)
        self.setWindowFlags(Qt.FramelessWindowHint)  # 移除边框和标题栏
        self.setAttribute(Qt.WA_TranslucentBackground)  # 设置背景为透明

        # 根据原始窗口句柄 创建为一个 QWindow 对象（即win32窗口的代理）
        self.window = QWindow.fromWinId(hwnd)

        style = win32gui.GetWindowLong(hwnd, win32con.GWL_STYLE)
        exstyle = win32gui.GetWindowLong(hwnd, win32con.GWL_EXSTYLE)
        print(f'save: {hwnd} {style} {exstyle}')

        self.window.setVisible(True)
        self.window.hwnd = hwnd       # 窗口句柄
        # self.window.phwnd = _phwnd     # 父窗口句柄
        self.window.style = style      # 窗口样式
        self.window.exstyle = exstyle  # 窗口额外样式

        # 设置布局
        layout = QVBoxLayout(self)
        layout.addWidget(self.window)


if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())
