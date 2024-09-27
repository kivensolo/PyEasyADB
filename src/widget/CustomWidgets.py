import typing

from PyQt5 import QtWidgets, QtGui
from PyQt5.QtCore import Qt, QRegExp
from PyQt5.QtGui import QIcon, QRegExpValidator, QFont, QTextCursor
from PyQt5.QtWidgets import QLineEdit, QComboBox, QTextBrowser, QAction, QMenu, QPushButton, QTextEdit

from src.logcat.log import z_logger
from utils.Tools import getWRYHFontStyle, getSimpleFontStyle
from utils.UITools import IconTool, ActionJudge


class CustomLineEdit(QLineEdit):
    """
    针对DeleteableComboBox实现的拦截Key_Enter事件的输入框
    目的是规避原生控件按回车后，会自动额外添加一个没删除按钮的数据项，所以用这个操作来规避此问题。
    """
    def __init__(self, parent=None):
        super(CustomLineEdit, self).__init__(parent)
        self.enterAccept = None
        validator = QRegExpValidator(QRegExp("^[a-z][a-z0-9_.]*$"), self)
        self.setValidator(validator)
        self.setFont(getSimpleFontStyle(9))

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

    def initData(self, list_data):
        for pos, device in enumerate(list_data):
            if pos == 0:
                self.mainWindow.pkgManager.setSelectedPackageName(device[0])
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
                    z_logger.info_with_stamp(f"删除{package_name}成功")
                else:
                    z_logger.error(f"删除{package_name}失败:{value}")
                break

    def onComBoxIndexChanged(self):
        _index = self.currentIndex()
        _text = self.currentText()
        if _index == -1:
            self.mainWindow.pkgManager.setSelectedPackageName("")
            return
        if _text != "":  # 第一次加载时，有了数据，但也是为空字符串，因此单独处理
            self.mainWindow.pkgManager.setSelectedPackageName(_text)
            z_logger.info(f"切换目标应用为:{_text}")


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
        if ActionJudge.isAcceptDrag(event):
            event.accept()
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


class LiveLogTextBrowser(QTextBrowser):
    # """
    # 具备按条件做过滤的TextBrowser
    # 用于日志实时打印
    # """

    def __init__(self, parent=None):
        super(LiveLogTextBrowser, self).__init__(parent)
        self.needScrollToEnd = False
        self.setOpenLinks(True)
        self.setOpenExternalLinks(True)
        self.setReadOnly(True)
        self.unsetCursor()
        font = QFont("Microsofy YaHei Light", 11)
        self.setFont(font)

        self.setContextMenuPolicy(Qt.CustomContextMenu)
        self.customContextMenuRequested.connect(self.showContextMenu)

        self.menu = QMenu(self)
        self.action_copy = self.menu.addAction("Copy")
        self.action_copy.setShortcut("Ctrl+C")
        self.action_copy.triggered.connect(self.copy)
        self.action_select = self.menu.addAction("Select All")
        self.action_select.setShortcut("Ctrl+A")
        self.action_select.triggered.connect(self.selectAll)

        self.menu.addSeparator()
        clearIcon = IconTool.buildQIcon("ic_clear.png", "icons")
        self.action_clear = self.menu.addAction(clearIcon, "Clear All")
        self.action_clear.triggered.connect(self.clear)

    def setAlwaysScrollToEnd(self, isEnable):
        self.needScrollToEnd = isEnable

    def showContextMenu(self, position):
        cursor = self.textCursor()
        self.action_copy.setEnabled(cursor.hasSelection())
        hasContent = self.document().lineCount() > 1
        self.action_select.setEnabled(hasContent)
        self.action_clear.setEnabled(hasContent)

        self.menu.exec_(self.mapToGlobal(position))

    def contextMenuEvent(self, e: typing.Optional[QtGui.QContextMenuEvent]) -> None:
        self.showContextMenu(e.pos())

    def wheelEvent(self, event):
        if event.modifiers() == Qt.ControlModifier:
            # 禁止 Ctrl+鼠标滑轮缩放
            event.ignore()
        else:
            super().wheelEvent(event)

    def show_context_menu(self, pos):
        self.menu = QMenu(self)
        clear_action = QAction("Clear", self)
        clear_action.triggered.connect(self.clear)
        self.menu.addAction(clear_action)
        self.menu.exec_(self.viewport().mapToGlobal(pos))

    def append(self, text: typing.Optional[str]) -> None:
        if self.needScrollToEnd:
            self.moveCursor(QTextCursor.End)
            self.ensureCursorVisible()
        return super().append(text)


class StatePushButton(QPushButton):
    def __init__(self, parent=None):
        super().__init__(parent)
        self.isPressed = False

    def mousePressEvent(self, event):
        if event.button() == Qt.LeftButton:
            if self.isPressed:
                self.setStyleSheet("background-color: #00ffffff;")
                self.isPressed = False
            else:
                self.setStyleSheet("background-color: #d4d4d4;")
                self.isPressed = True
        return super().mousePressEvent(event)


class HoverQLineEdit(QLineEdit):
    """
    悬浮时border会发光的自定义控件
    """
    def __init__(self, parent=None):
        super(HoverQLineEdit, self).__init__(parent)
        self._hover = False  # 默认不处于悬停状态
        self.setStyleSheet(self.styleSheet() + self.base_style())

    def enterEvent(self, event):
        self._hover = True
        self.update()
        super().enterEvent(event)

    def leaveEvent(self, event):
        self._hover = False
        self.update()
        super().leaveEvent(event)

    def base_style(self):
        """
        设置样式
        transition属性：
        transition: box-shadow 0.3s ease;  /* 平滑过渡 */
        用于定义当某个属性改变时应该发生的效果，

        这里设置了 box-shadow 的变化会在0.3秒内平滑过渡。
        box-shadow: 0 0 10px rgba(0, 0, 252, 0.5)
        box-shadow 的语法是：
        horizontal-offset vertical-offset blur-radius  color
          水平偏移          垂直偏移         模糊半径     RGBA 颜色模式，其中最后一个值表示透明度

        但是会报错：
        Unknown property transition
        Unknown property box-shadow
        所以删除。
        :return:
        """
        return """
            QLineEdit {
                border: 1px solid #ccc;
                padding: 3px;
                background-color: white;
            }
            QLineEdit:hover {
                border: 2px solid rgb(131, 212, 252);
            }
        """


class HoverQTextEdit(QTextEdit):
    """
    悬浮时border会发光的自定义控件
    """
    def __init__(self, parent=None):
        super(HoverQTextEdit, self).__init__(parent)
        self._hover = False  # 默认不处于悬停状态
        self.setStyleSheet(self.styleSheet() + self.base_style())

    def enterEvent(self, event):
        self._hover = True
        self.update()
        super().enterEvent(event)

    def leaveEvent(self, event):
        self._hover = False
        self.update()
        super().leaveEvent(event)

    def base_style(self):
        """
        设置样式
        transition属性：用于定义当某个属性改变时应该发生的效果，
                    这里设置了 box-shadow 的变化会在0.3秒内平滑过渡。

        box-shadow 的语法是：
        horizontal-offset vertical-offset blur-radius  color
          水平偏移          垂直偏移         模糊半径     RGBA 颜色模式，其中最后一个值表示透明度
        :return:
        """
        return """
            QTextEdit {
                border: 1px solid #ccc;
                padding: 3px;
                background-color: white;
            }
            QTextEdit:hover {
                border: 2px solid rgb(131, 212, 252);
            }
        """

