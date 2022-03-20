#!/usr/bin/python
# -*- coding: utf-8 -*-
import sys

from PyQt5.QtWidgets import QTabWidget, QTabBar, QApplication

from ui.widget.ConsoleView import ConsoleWindow
from utils.UITools import IconTool
from utils.Utils import Utils


class ButtomWindow(QTabWidget):
    """
    底部TabWidget控件
    """
    def __init__(self, parent=None):
        super(ButtomWindow, self).__init__(parent)
        self.consoleView = ConsoleWindow()
        self.tabBar = QTabBar()
        self.init_ui()

    def init_ui(self):
        self.tabBar.tabBarClicked.connect(self.status)
        self.tabBar.setExpanding(False)
        self.setTabBar(self.tabBar)
        # 将console添加至buttom组件中
        self.addTab(self.consoleView, IconTool.buildQIcon("logcat.png"), "Logcat")
        # self.consoleView.setVisible(False)
        # self.setFixedHeight(Utils.getItemHeight())
        self.consoleView.setVisible(True)
        self.setMaximumHeight(Utils.getWindowHeight())
        self.setTabPosition(QTabWidget.South)
        self.setStyleSheet(
            "QTabBar::tab {"
            "border: none; height: " + str(Utils.getItemHeight()) + "px; width:100px;"
            "color:black;"
            "} "
            "QTabBar::tab:selected { "
            "border: none;"
            "background: lightgray; "
            "} "
        )

    def status(self):
        # 槽函数
        if self.tabBar.tabText(self.tabBar.currentIndex()) == 'Logcat':
            if self.consoleView.isVisible():
                self.consoleView.setVisible(False)
                self.preHeight = self.width()
                self.setFixedHeight(Utils.getItemHeight())
            else:
                self.consoleView.setVisible(True)
                self.setMaximumHeight(Utils.getWindowHeight())


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = ButtomWindow()
    mainWin.show()
    sys.exit(app.exec_())
