import sys
import xml.dom.minidom

from PyQt5 import QtCore, QtGui, QtWidgets
from PyQt5.QtCore import Qt, pyqtSlot
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import QWidget, QHBoxLayout, QApplication, QPushButton, QLineEdit

from logcat.log import z_logger
from ui import MainWindow
from utils.Tools import getWRYHFontStyle, getKTFontStyle


class CommonFunctionalWidget(QWidget):
    """
    中间内容的主要布局
    """

    def __init__(self, parent: MainWindow):
        super().__init__()
        self.parent = parent

        self.setAttribute(Qt.WA_StyledBackground)
        self.setStyleSheet("background-color:#fafafa")

        self._init_convenient_area()

        # self.root_layout = QVBoxLayout()
        # self.setLayout(self.root_layout)
        #
        # self._init_class_path_layout()
        # self.root_layout.addStretch()

    # 新版布局逻辑
    def _init_convenient_area(self):
        convenient_area = Ui_ConvenientArea()
        convenient_area.setUpUiDynamic(self)
        # TODO 点击行为测试
        # convenient_area.stop_app_v2.clicked.connect(self.testFun())

    def testFun(self):
        self.parent.doAdbActrion("force-stop {0}")

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
    def setUpUiDynamic(self, ConvenientArea):
        ConvenientArea.setObjectName("ConvenientArea")
        # ConvenientArea.setStyleSheet("border:2px solid green")

        # 此UI区域的根布局
        self.ui_root_vlayout = QtWidgets.QVBoxLayout(ConvenientArea)
        self.ui_root_vlayout.setObjectName("functionVLayout")
        self.scrollArea = QtWidgets.QScrollArea(ConvenientArea)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.scrollArea.sizePolicy().hasHeightForWidth())
        self.scrollArea.setSizePolicy(sizePolicy)
        self.scrollArea.setStyleSheet("")
        self.scrollArea.setWidgetResizable(True)
        self.scrollArea.setObjectName("scrollArea")
        self.scrollAreaWidgetContents = QtWidgets.QWidget()
        self.scrollAreaWidgetContents.setGeometry(QtCore.QRect(0, 0, 272, 318))
        self.scrollAreaWidgetContents.setObjectName("scrollAreaWidgetContents")
        # 滚动区域为垂直layout
        self.vlayout_of_scrollarea = QtWidgets.QVBoxLayout(self.scrollAreaWidgetContents)
        self.vlayout_of_scrollarea.setObjectName("scroll_area_layout")

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
                # 每个行为控件创建布局
                v_lyout = QtWidgets.QVBoxLayout()
                v_lyout.setObjectName("vlayout_%s_%s".format(rowIndex, columnIndex))
                spacerItem = QtWidgets.QSpacerItem(20, 10, QtWidgets.QSizePolicy.Minimum, QtWidgets.QSizePolicy.Expanding)
                v_lyout.addItem(spacerItem)

                attrs = item.getElementsByTagName("attr")

                item_desc_view = None
                item_icon_view = None
                for attr in attrs:
                    attr_name = attr.getAttribute('name')
                    attr_value = attr.firstChild.nodeValue
                    if attr_name == "desc":
                        item_desc_view = QtWidgets.QLabel(_groupBox)
                        item_desc_view.setEnabled(True)
                        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Fixed)
                        sizePolicy.setHorizontalStretch(0)
                        sizePolicy.setVerticalStretch(1)
                        sizePolicy.setHeightForWidth(item_desc_view.sizePolicy().hasHeightForWidth())
                        item_desc_view.setSizePolicy(sizePolicy)
                        item_desc_view.setText(attr_value)
                        item_desc_view.setAlignment(QtCore.Qt.AlignCenter)
                        item_desc_view.setObjectName("item_label_" + str(index))
                        v_lyout.addWidget(item_desc_view)
                    elif attr_name == "icon":
                        item_icon_view = QtWidgets.QLabel(_groupBox)
                        item_icon_view.setEnabled(True)
                        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Fixed)
                        sizePolicy.setHorizontalStretch(0)
                        sizePolicy.setVerticalStretch(1)
                        sizePolicy.setHeightForWidth(item_icon_view.sizePolicy().hasHeightForWidth())
                        item_icon_view.setSizePolicy(sizePolicy)
                        item_icon_view.setText("")
                        if len(attr_value) > 0:
                            item_icon_view.setPixmap(QtGui.QPixmap(attr_value))
                        else:
                            print(attr_name + "未配置图标！！！")
                        item_icon_view.setAlignment(QtCore.Qt.AlignCenter)
                        item_icon_view.setObjectName("item_icon_" + str(index))
                        v_lyout.addWidget(item_icon_view)

                    elif attr_name == "cmd":
                        item_icon_view.setStatusTip(attr_value)
                gridLayout.addLayout(v_lyout, rowIndex, columnIndex, 1, 1)

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