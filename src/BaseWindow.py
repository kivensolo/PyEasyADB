"""

窗口基类, 设置窗口图标, 标题, 居中等属性

author: KingZ
last edited: 2022.03.19
"""
from PyQt5.QtWidgets import QMainWindow, QDesktopWidget, QApplication

from utils.UITools import IconTool, UiUtils
from utils.Utils import Utils


class BaseWindow(QMainWindow):
    def __init__(self):
        super().__init__()
        self.title = "Easy ADB"
        self._init_windows_size()

    def initWindow(self):
        # 窗口初始化
        self.setObjectName("MainWindow")
        # self.setToolTip('This is a <b>QMainWindow</b> widget')
        self.setWindowTitle(self.title)
        self.setWindowIcon(IconTool.buildQIcon("logo.png"))

        # 设置主界面背景色
        # self.window.setStyleSheet("background-color:rgb(255,255,255)")
        self.center()

    @staticmethod
    def _init_windows_size():
        __desktop = QApplication.desktop()
        qRect = __desktop.screenGeometry()
        UiUtils.setWindowWidth(qRect.width())
        UiUtils.setWindowHeight(qRect.height())

    # 设置窗口居中
    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())
