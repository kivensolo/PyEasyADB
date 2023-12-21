import sys

from PyQt5 import QtCore
from PyQt5.QtCore import Qt, QSize, pyqtSlot
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import QWidget, QHBoxLayout, QVBoxLayout, QLabel, QComboBox, QApplication, QPushButton, QLineEdit
from PyQt5.uic.uiparser import QtWidgets

from logcat.log import z_logger
from ui.component.ConvenientOperateWidget import Ui_ConvenientArea
from utils.Tools import getWRYHFontStyle, getKTFontStyle


class CommonFunctionalWidget(QWidget):
    """
    中间内容的主要布局
    """

    def __init__(self, parent):
        super().__init__()
        self.parent = parent

        self.setAttribute(Qt.WA_StyledBackground)
        self.setStyleSheet("background-color:#fafafa")

        # TODO 通过配置动态布局
        Ui_ConvenientArea().setupUi(self)

        # self.root_layout = QVBoxLayout()
        # self.setLayout(self.root_layout)
        #
        # self._init_class_path_layout()
        # self.root_layout.addStretch()

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



if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = CommonFunctionalWidget(None)
    mainWin.show()
    sys.exit(app.exec_())