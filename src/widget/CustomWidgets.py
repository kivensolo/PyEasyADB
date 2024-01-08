from IPython.external.qt_for_kernel import QtGui
from PyQt5 import QtWidgets
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QIcon
from PyQt5.QtWidgets import QLineEdit, QComboBox

from src.logcat.log import z_logger
from utils.Tools import getWRYHFontStyle
from utils.UITools import IconTool


class CustomLineEdit(QLineEdit):
    """
    针对DeleteableComboBox实现的拦截Key_Enter事件的输入框
    目的是规避原生控件按回车后，会自动额外添加一个没删除按钮的数据项，所以用这个操作来规避此问题。
    """
    def __init__(self, parent=None):
        super(CustomLineEdit, self).__init__(parent)
        self.enterAccept = None

    def setEnterAccept(self, block):
        self.enterAccept = block

    def keyPressEvent(self, event: QtGui.QKeyEvent) -> None:
        if event.key() == Qt.Key_Return or event.key() == Qt.Key_Enter:
            # 在这里处理回车键事件(只进行回调)
            self.enterAccept()
            # 阻止默认的回车键行为
            event.ignore()
        else:
            super().keyPressEvent(event)


class DeleteableComboBox(QComboBox):
    """
    可将数据项删除的选择列表控件
    """
    def __init__(self, parent=None):
        super(DeleteableComboBox, self).__init__(parent)
        self.mainWindow = None
        self.currentChooseApp = ''
        # 先设置10个, 减少bug出现的几率。此bug为: 添加数据项超过可视范围数量后，再添加两个，删除按钮就不见了。
        self.setMaxVisibleItems(10)
        self.setFont(getWRYHFontStyle())
        self.view().setVerticalScrollBarPolicy(Qt.ScrollBarAsNeeded)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.sizePolicy().hasHeightForWidth())
        self.setSizePolicy(sizePolicy)
        # self.setInsertPolicy(QComboBox.InsertAtTop) InsertAtTop无效  参见:https://blog.csdn.net/ji3009/article/details/119107371

        self.setEditable(True)  # 变成了一个 QLineEdit 和 QComboBox 的组合
        customLineEdit = CustomLineEdit()
        customLineEdit.setEnterAccept(self.onEnterPressClicekd)
        customLineEdit.setPlaceholderText("输入目标应用包名，按回车键确认")
        self.setLineEdit(customLineEdit)

        # 创建QListWidget用于显示自定义项
        self.listWidget = QtWidgets.QListWidget()
        # 设置QComboBox的视图为QListWidget
        self.setView(self.listWidget)
        # 设置QComboBox的模型为QListWidget的模型
        self.setModel(self.listWidget.model())
        self.currentIndexChanged.connect(self.onComBoxIndexChanged)

    def setMainWinodw(self, window):
        self.mainWindow = window

    def onEnterPressClicekd(self):
        text = self.lineEdit().text()
        if text == "":
            return
        isExist = self.mainWindow.pkgManager.isPackageExist(text)
        if isExist:
            z_logger.error("应用已存在,无需重复添加!")
        else:
            result, value = self.mainWindow.pkgManager.addPackage(text)
            if result:
                z_logger.info_with_stamp(f"添加应用成功:{text}")
                item_widget: QtWidgets.QWidget = self._deleteBtn(text)
                item_wrap = QtWidgets.QListWidgetItem()
                item_wrap.setFont(getWRYHFontStyle())
                item_wrap.setText(text)
                # 在第一行插入item_wrap和item_widget
                self.listWidget.insertItem(0, item_wrap)
                self.listWidget.setItemWidget(item_wrap, item_widget)
                # 更新QComboBox视图
                self.update()
            else:
                z_logger.error(f"添加应用失败:{value}")

    def addItemsWithData(self, list_data):
        for pos, device in enumerate(list_data):
            item_widget: QtWidgets.QWidget = self._deleteBtn(device[0])
            item_wrap = QtWidgets.QListWidgetItem(self.listWidget)
            item_wrap.setFont(getWRYHFontStyle())
            item_wrap.setText(device[0])
            self.listWidget.setItemWidget(item_wrap, item_widget)

    def _deleteBtn(self, package_name):
        qWidget = QtWidgets.QWidget()
        deledeButton = QtWidgets.QPushButton()
        deledeButton.setStyleSheet("background:transparent;")  # "border:1px solid red;")
        deledeButton.setIcon(QIcon(IconTool.buildQIcon('close.png', dir="icons")))
        deledeButton.clicked.connect(lambda: self._onComBoxItemDelete(package_name))
        boxLayout = QtWidgets.QHBoxLayout()
        boxLayout.addStretch()
        boxLayout.addWidget(deledeButton)
        boxLayout.setContentsMargins(0, 0, 0, 0)
        boxLayout.setSpacing(5)
        qWidget.setLayout(boxLayout)
        return qWidget

    def _onComBoxItemDelete(self, package_name):
        for index in range(self.count()):
            text = self.itemText(index)
            if package_name == text:
                self.removeItem(index)
                result, value = self.mainWindow.pkgManager.exec(f"delete from package where name = \'{text}\'")
                if result:
                    z_logger.info(f"删除{package_name}成功")
                else:
                    z_logger.error(f"删除{package_name}失败:{value}")
                break

    def onComBoxIndexChanged(self):
        self.currentChooseApp = self.currentText()
        # z_logger.info("切换目标应用为:" + self.currentChooseApp)


class DraggableLineEdit(QLineEdit):
    """
    可接收文件拖动进入事件的自定义QLineEdit
    """
    def __init__(self, parent=None):
        super(DraggableLineEdit, self).__init__(parent)
        self.block = None
        self.setAcceptDrops(True)
        self.setFont(getWRYHFontStyle())

    def dragEnterEvent(self, event):
        if event.mimeData().hasUrls():
            url = event.mimeData().urls()[0]
            if url.scheme() == "file":
                path = url.toLocalFile()
                if path.endswith('.apk'):  # 检查文件是否是 .apk 文件
                    event.accept()
                else:
                    event.ignore()
        else:
            event.ignore()

    def dropEvent(self, event):
        if event.mimeData().hasUrls():
            url = event.mimeData().urls()[0]
            if url.scheme() == "file":
                path = url.toLocalFile()
                if path.endswith('.apk'):  # 检查文件是否是 .apk 文件
                    self.setText(path)
                    event.accept()
                    # 事件回调
                    if self.block is not None:
                        self.block(path)
                else:
                    event.ignore()
        else:
            event.ignore()

    def setDropEventListerner(self, block):
        self.block = block

