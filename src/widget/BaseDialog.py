#!/usr/bin/python
# -*- coding: utf-8 -*-

from PyQt5.QtGui import QDragEnterEvent, QDropEvent
from PyQt5.QtWidgets import QDesktopWidget, QDialog

from utils.UITools import ActionJudge, IconTool


class BaseDialog(QDialog):
    def __init__(self, title):
        super().__init__()
        self.title = title

    def initWindow(self):
        self.setWindowTitle(self.title)
        self.setWindowIcon(IconTool.buildQIcon('logo.png'))
        self.resize(500, 300)
        self.center()

    # 设置窗口居中
    def center(self):
        qr = self.frameGeometry()
        cp = QDesktopWidget().availableGeometry().center()
        qr.moveCenter(cp)
        self.move(qr.topLeft())


class DragDialog(BaseDialog):
    """
    实现拖放（Drag and Drop）功能的弹窗
    """
    def __init__(self, title):
        super().__init__(title)
        self.title = title
        self.setAcceptDrops(True)

    def dragEnterEvent(self, event: QDragEnterEvent):
        if ActionJudge.isAcceptDrag(event):
            event.accept()
        else:
            event.ignore()

    def dropEvent(self, event: QDropEvent):
        event.acceptProposedAction()
