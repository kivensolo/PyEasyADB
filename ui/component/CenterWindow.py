import sys
import xml.dom.minidom

from PyQt5 import QtCore, QtGui, QtWidgets
from PyQt5.QtCore import Qt, pyqtSlot, QSize
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import QWidget, QHBoxLayout, QApplication, QPushButton, QLineEdit

from logcat.log import z_logger
from ui import MainWindow
from utils.ADBTools import ADBCmdParams
from utils.Tools import getWRYHFontStyle, getKTFontStyle


class CommonFunctionalWidget(QWidget):
    """
    中间内容的主要布局
    """

    def __init__(self, parent: MainWindow):
        super().__init__()
        self.parent = parent

        self.setAttribute(Qt.WA_StyledBackground)
        # self.setStyleSheet("background-color:white")

        self._init_convenient_area()

        # self.root_layout = QVBoxLayout()
        # self.setLayout(self.root_layout)
        #
        # self._init_class_path_layout()
        # self.root_layout.addStretch()

    # 新版布局逻辑
    def _init_convenient_area(self):
        convenient_area = Ui_ConvenientArea(self.parent)
        convenient_area.setUpUiDynamic(self)

    def _init_class_path_layout(self):
        """
         Activity类路径
        :return:
        """
        self.setStyleSheet("border: 1px solid #00ff00")


        self.action_start = QPushButton(self)
        self.action_start.setText("Start")
        self.action_start.setGeometry(QtCore.QRect(0, 0, 81, 31))
        self.action_start.setObjectName("act_start")
        self.action_start.setFont(getWRYHFontStyle())
        self.action_start.clicked.connect(lambda: self.invokePkgAction())
        self.activity_class_layout = QHBoxLayout(self)
        self.activityClassPath = QLineEdit(self)
        self.activityClassPath.setPlaceholderText("input activity class path")
        self.activityClassPath.setGeometry(QtCore.QRect(0, 0, 291, 40))
        self.activityClassPath.setObjectName("class_path")
        self.activityClassPath.setFont(getKTFontStyle(size=12, font=QFont.System))

        self.activity_class_layout.addWidget(self.action_start)
        self.activity_class_layout.addWidget(self.activityClassPath)

        self.activity_class_layout.addStretch()
        self.root_layout.addLayout(self.activity_class_layout)

    def invokePkgAction(self):
        if not self.parent.is_current_device_connect():
            return
        currentPkgName = ""
        if currentPkgName:
            class_Path = "{0}/{1}".format(currentPkgName, self.activityClassPath.text())
            self.parent.adbTools.start_app_page(self.current_ip, class_Path, self._onInvokeActionEnd)

    @pyqtSlot(list)
    def _onInvokeActionEnd(self, result):
        for line in result:
            if "Error:" in line:
                z_logger.error("操作错误:" + str(line))
                return
            elif "Failure" in line:
                z_logger.info("操作失败:" + str(line))
                return
            elif "Unknown package" in line:
                # 卸载不存在应用的时候，adb会抛异常，但是python输出流无法捕获
                z_logger.error("操作失败，请确认目标设备中存在此应用:" + str(line))
        z_logger.debug("操作完毕")

    def dynamicSetupUi(self, parent):
        parent.setObjectName("dynamic_functions")
        self.verticalLayout = QtWidgets.QVBoxLayout(parent)
        self.verticalLayout.setObjectName("verticalLayout")


# UI模板配置文件路径
template_ui_config_file_path = "./config/function_templates.xml"
# 每个ui模板一行的元素个数
every_row_size = 5


class Ui_ConvenientArea(object):
    def __init__(self, mainWidow):
        self.mainWindow = mainWidow

    def setUpUiDynamic(self, ConvenientArea):
        ConvenientArea.setObjectName("ConvenientArea")

        # 此UI区域的根布局
        self.ui_root_vlayout = QtWidgets.QVBoxLayout(ConvenientArea)
        self.ui_root_vlayout.setObjectName("functionVLayout")
        self.scrollArea = QtWidgets.QScrollArea(ConvenientArea)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.scrollArea.sizePolicy().hasHeightForWidth())
        self.scrollArea.setSizePolicy(sizePolicy)
        self.scrollArea.setStyleSheet("""
            QScrollArea{
                border: 0px solid red;
            };
        """)
        self.scrollArea.setWidgetResizable(True)
        self.scrollArea.setObjectName("scrollArea")
        self.scrollAreaWidgetContents = QtWidgets.QWidget()
        self.scrollAreaWidgetContents.setGeometry(QtCore.QRect(0, 0, 272, 318))
        self.scrollAreaWidgetContents.setObjectName("scrollAreaWidgetContents")
        # 滚动区域为垂直layout
        self.vlayout_of_scrollarea = QtWidgets.QVBoxLayout(self.scrollAreaWidgetContents)
        self.vlayout_of_scrollarea.setObjectName("scroll_area_layout")

        # if __name__ == "__main__":
        #     template_ui_config_file_path = "../../config/function_templates.xml"

        # 动态设置groupView
        dom = xml.dom.minidom.parse(template_ui_config_file_path)
        root = dom.documentElement
        template_list = root.getElementsByTagName("template")
        for template in template_list:
            template_name = template.getAttribute('name')
            # 模板区域数量检查，创建分组的GroupBox
            _groupBox = QtWidgets.QGroupBox(self.scrollAreaWidgetContents)
            sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
            sizePolicy.setHorizontalStretch(0)
            sizePolicy.setVerticalStretch(0)
            sizePolicy.setHeightForWidth(_groupBox.sizePolicy().hasHeightForWidth())
            _groupBox.setSizePolicy(sizePolicy)
            _groupBox.setCheckable(False)
            _groupBox.setObjectName(template_name)
            _groupBox.setTitle(template_name)

            item_list = template.getElementsByTagName("item")
            # 模板布局
            gridLayout = QtWidgets.QGridLayout(_groupBox)
            gridLayout.setObjectName("template_" + template_name)

            # 每个模板区域有几个行为控件
            current_template_item_counts = len(item_list)
            for index in range(current_template_item_counts):
                item = item_list[index]
                rowIndex = int(index / every_row_size)
                columnIndex = index % every_row_size

                # 初始化每一个tool按钮
                item_tool_button = QtWidgets.QToolButton(_groupBox)
                item_tool_button.setAutoRaise(True)
                sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
                sizePolicy.setHorizontalStretch(0)
                sizePolicy.setVerticalStretch(0)
                item_tool_button.setSizePolicy(sizePolicy)
                attrs = item.getElementsByTagName("attr")
                cmdParams = ADBCmdParams()
                for attr in attrs:
                    _key = attr.getAttribute('name')
                    _value = attr.firstChild.nodeValue
                    if _key == "text":
                        font = QtGui.QFont()
                        font.setPointSize(10)
                        item_tool_button.setText(_value)
                        item_tool_button.setFont(font)
                    elif _key == "icon":
                        item_tool_button.setIcon(QtGui.QIcon(_value))
                        item_tool_button.setIconSize(QSize(56, 56))
                        item_tool_button.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextUnderIcon)
                    elif _key == "cmd":
                        shellValue = attr.getAttribute("shell")
                        if shellValue.lower() == "false":
                            cmdParams.isShellMode = False
                        cmdParams.cmd_with_format = _value
                # https://blog.csdn.net/PixelNovaO/article/details/132727483
                item_tool_button.clicked.connect(lambda: self.mainWindow.runADBCmd(cmdParams))
                gridLayout.addWidget(item_tool_button, rowIndex, columnIndex, 1, 1)

            # 若第一行未满，则进行填充, 使UI按照网格对齐
            if current_template_item_counts < every_row_size and \
                    current_template_item_counts % every_row_size != 0:
                last_row_index = int(current_template_item_counts / every_row_size)
                inflate_item_start_index = current_template_item_counts % every_row_size
                inflate_counts = every_row_size - inflate_item_start_index
                for i in range(inflate_counts):
                    inflate_column_index = i + inflate_item_start_index
                    label = QtWidgets.QLabel(_groupBox)
                    label.setObjectName("inflate_0%s".format(inflate_column_index))
                    label.setObjectName("item_%s_%s".format(last_row_index, inflate_column_index))
                    gridLayout.addWidget(label, 0, inflate_column_index, 1, 1)

            # 将groupBox加入垂直滚动布局中
            self.vlayout_of_scrollarea.addWidget(_groupBox)

        # 最底部添加"弹簧"
        scroll_bottom_spacer_item = QtWidgets.QSpacerItem(20, 20, QtWidgets.QSizePolicy.Minimum, QtWidgets.QSizePolicy.Expanding)
        self.vlayout_of_scrollarea.addItem(scroll_bottom_spacer_item)
        # 给滚动区域设置Qwidgets
        self.scrollArea.setWidget(self.scrollAreaWidgetContents)
        self.ui_root_vlayout.addWidget(self.scrollArea)


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = CommonFunctionalWidget(None)
    mainWin.show()
    sys.exit(app.exec_())