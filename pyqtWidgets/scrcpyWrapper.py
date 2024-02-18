import subprocess
import sys
import time

import win32con
import win32gui
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QWindow
from PyQt5.QtWidgets import QApplication, QMainWindow, QVBoxLayout, QWidget, QPushButton, QLabel, QListWidget

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
        self.layout = QVBoxLayout()
        self.central_widget = QWidget()
        self.central_widget.setLayout(self.layout)
        self.setCentralWidget(self.central_widget)
        showBtn = QPushButton("获取所有可用、可视窗口", self, clicked=self._getWindowList, maximumHeight=30)
        self.layout.addWidget(showBtn)
        # btn.clicked.connect(self.onClick)
        self.layout.addWidget(QLabel('双击列表中的项目则进行嵌入目标窗口到下方\n格式为：句柄|父句柄|标题|类名', self))
        self.windowList = QListWidget(self, itemDoubleClicked=self.onItemDoubleClicked, maximumHeight=500)
        self.layout.addWidget(self.windowList)
        self.setMinimumSize(800, 700)
        # 自己的句柄
        self.myhwnd = int(self.winId())

    def onClick(self):
        # 启动记事本
        # self.process = subprocess.Popen(['C:\\Users\\ZekeWang\\Downloads\\scrcpy-win64-v2.3.1\\scrcpy-win64-v2.3.1\\scrcpy.exe'])
        self.process = subprocess.Popen(['msinfo32.exe'])
        # 等待记事本启动
        time.sleep(2)
        # # 获取前置窗口句柄
        hwnd = win32gui.GetForegroundWindow()
        # 创建一个win32窗口的代理
        embed_window: QWindow = QWindow.fromWinId(hwnd)
        embed_window.setTitle("XXXXXXXXX")
        self.container: QWidget = QWidget.createWindowContainer(embed_window)
        self.container.setMinimumHeight(500)
        self.container.setWindowFlags(Qt.Window)
        # self.container.setParent(self)
        self.layout.addWidget(self.container)
        self.layout.setStretch(0, 0)
        self.layout.setStretch(1, 0)
        self.layout.setStretch(2, 5)
        self.layout.setStretch(3, 8)

    def onItemDoubleClicked(self, item):
        """列表双击选择事件"""
        # 先移除掉item
        self.windowList.takeItem(self.windowList.indexFromItem(item).row())
        hwnd, phwnd, _, _ = item.text().split('|')
        # 开始嵌入

        if self.layout.count() == 4:
            # 如果数量等于4说明之前已经嵌入了一个窗口，现在需要把它释放出来
            self.layout.removeWidget(3)
        _hwnd, _phwnd = int(hwnd), int(phwnd)
        # 嵌入之前的属性
        style = win32gui.GetWindowLong(_hwnd, win32con.GWL_STYLE)
        exstyle = win32gui.GetWindowLong(_hwnd, win32con.GWL_EXSTYLE)
        print('save', _hwnd, style, exstyle)

        # 创建一个win32窗口的代理
        embed_window: QWindow = QWindow.fromWinId(_hwnd)
        # embed_window.setStyleSheet("background-color: #00ff00")
        # embed_window.setWindowFlags(Qt.Window)
        widget = QWidget.createWindowContainer(embed_window)
        widget.setWindowFlags(Qt.Window)
        widget.hwnd = _hwnd  # 窗口句柄
        widget.phwnd = _phwnd  # 父窗口句柄
        widget.style = style  # 窗口样式
        widget.exstyle = exstyle  # 窗口额外样式
        self.layout.addWidget(widget)

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
            self.windowList.addItem(
                f'{hwnd:<10}|{phwnd:<10}|标题：{title:<10}|类名：{name:<10}')


if __name__ == '__main__':
    app = QApplication(sys.argv)
    window = MainWindow()
    window.show()
    sys.exit(app.exec_())
