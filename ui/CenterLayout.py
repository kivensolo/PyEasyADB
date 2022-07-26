import sys

from PyQt5 import QtCore
from PyQt5.QtCore import Qt, QSize, pyqtSlot
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import QWidget, QHBoxLayout, QVBoxLayout, QLabel, QComboBox, QApplication, QPushButton, QLineEdit
from logcat.log import z_logger
from utils.Tools import getWRYHFontStyle, getKTFontStyle


class CenterLayout(QWidget):
    """
    工具的中间主要布局
    """
    resImages = {'Device': '././res/img/device.png'}

    def __init__(self, parent):
        super().__init__()
        self.parent = parent

        self.setAttribute(Qt.WA_StyledBackground)
        # self.setStyleSheet("background-color:#FFAAFF");
        self.setStyleSheet('''''')
        self.root_layout = QVBoxLayout()
        self.setLayout(self.root_layout)

        # 设备名称等信息
        self._init_package_add_layout()
        self._init_class_path_layout()

        self.root_layout.addLayout(self.package_edit_layout)
        self.root_layout.addLayout(self.activity_class_layout)
        self.root_layout.addStretch()

    def _init_class_path_layout(self):
        """
         Activity类路径
        :return:
        """
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

    def _init_package_add_layout(self):
        """
        新增包名的layout
        :return:
        """
        self.package_edit_layout = QHBoxLayout(self)
        # self.package_edit_layout.alignment()
        self.btnAddNewPkg = QPushButton(self)
        self.btnAddNewPkg.setText("包名添加")
        # self.btnAddNewPkg.setStyleSheet("""
        #    QPushButton{
        #         background-color: #A0A0A0 ;
        #     }
        #
        #     QPushButton:hover {
        #         border: 1px solid #C0C0C0;
        #         background-color: yellow;
        #         border-style: inset;
        #         border-radius:2px;
        #         background-color:#C0C0C0;
        #         border-style: solid;
        #     }
        # """)
        # self.btnAddNewPkg.setGeometry(QtCore.QRect(10, 100, 101, 31))
        self.btnAddNewPkg.setObjectName("btn_addNewPkg")
        self.btnAddNewPkg.setFont(getKTFontStyle())
        self.btnAddNewPkg.clicked.connect(lambda: self.on_package_add())
        self.pkg_inputEditText = QLineEdit(self)
        self.pkg_inputEditText.setGeometry(QtCore.QRect(0, 0, 261, 31))
        self.pkg_inputEditText.setObjectName("package_add")
        self.pkg_inputEditText.setFont(getWRYHFontStyle(12))
        self.package_edit_layout.addWidget(self.btnAddNewPkg)
        self.package_edit_layout.addWidget(self.pkg_inputEditText)
        self.package_edit_layout.addStretch()  # 添加可拉伸弹簧

    def invokePkgAction(self):
        if not self.parent.is_current_device_connect():
            return
        currentPkgName = self.get_current_choose_pkg()
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


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = CenterLayout(None)
    mainWin.show()
    sys.exit(app.exec_())