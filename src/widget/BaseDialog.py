#!/usr/bin/python
# -*- coding: utf-8 -*-

from PyQt5.QtGui import QDragEnterEvent, QDropEvent
from PyQt5.QtWidgets import QDesktopWidget, QDialog, QApplication

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
        #FIXME 弹窗位置要优化，多屏设备的时候，在屏幕2点击，会展示在屏幕1中心.
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
        """
        文件被拖进来的事件回调， 用于判断是否接收此文件，
        目前统一用ActionJudge.isAcceptDrag判断,
        子类可以自定义复写此逻辑。
        :param event:
        :return:
        """
        if ActionJudge.isAcceptDrag(event):
            event.accept()
        else:
            event.ignore()

    def dropEvent(self, event: QDropEvent):
        event.acceptProposedAction()
