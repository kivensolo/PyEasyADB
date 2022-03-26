import sys

from PyQt5 import QtCore
from PyQt5.QtCore import Qt, QSize
from PyQt5.QtGui import QFont, QPixmap
from PyQt5.QtWidgets import QWidget, QHBoxLayout, QVBoxLayout, QLabel, QComboBox, QApplication, QPushButton, QLineEdit, \
    QGridLayout

from logcat.log import z_logger
from ui.sql import DBManager
from utils import ADBTools
from utils.Tools import getWRYHFontStyle, getKTFontStyle
from utils.UITools import IconTool
from utils.UiWidgts import AppDeviceLabel


class DeviceInfoDetail(QWidget):
    resImages = {'Linked': '.././res/img/link_32x32.png',
                 'Unlink': '././res/img/unlink_32x28.png',
                 'Device': '././res/img/device.png'}

    def __init__(self):
        super().__init__()
        self.dbManager = DBManager()

        self.setAttribute(Qt.WA_StyledBackground)
        # self.setStyleSheet("background-color:#FFAAFF");
        self.setStyleSheet('''''')
        self.root_layout = QVBoxLayout()
        self.setLayout(self.root_layout)

        # 设备名称等信息
        self._init_device_info_layout()

        # 包名控件初始化
        self._init_choose_pkg_layout()

        # 新增包名
        self._init_package_add_layout()

        # 类路径
        self._init_class_path_layout()

        self._init_actions_layout()

        self.root_layout.addLayout(self.device_name_layout)
        self.root_layout.addLayout(self.package_layout)
        self.root_layout.addLayout(self.package_edit_layout)
        self.root_layout.addLayout(self.activity_class_layout)
        self.root_layout.addLayout(self.actionLayout)
        self.root_layout.addStretch()

    def _init_device_info_layout(self):
        self.device_name_layout = QHBoxLayout(self)
        self.device_name_layout.setContentsMargins(0, 0, 0, 0)  # 设置水平布局在Widget内上下左右的间距
        self.device_name_layout.setSpacing(10)  # 设置间距
        self.device_name_layout.setDirection(0)  # 自左向右的布局
        self.device_name_layout.addSpacing(10)  # 左侧空隙
        self.deviceImageView = QLabel(self)
        self.deviceImageView.setPixmap(IconTool.buildQPixmap("device.png"))
        self.deviceImageView.setAlignment(Qt.AlignLeft)
        self.device_info_name = AppDeviceLabel()
        self.device_info_name.setText('ZTE-2.5.2-ALL')
        self.device_info_name.setObjectName("device_infos")
        self.device_info_name.setStyleSheet("""
            background-color: #C0C0C0 ;
        """)
        self.device_name_layout.addWidget(self.deviceImageView)
        self.device_name_layout.addWidget(self.device_info_name)
        self.device_name_layout.addStretch()

    def _init_choose_pkg_layout(self):
        self.selected_pkg = ''
        self.package_layout = QHBoxLayout(self)
        self.package_layout.alignment()
        self.pkg_Tip = AppDeviceLabel()
        self.pkg_Tip.setText('目标App包名:')
        self.pkg_Tip.setGeometry(QtCore.QRect(0, 0, 111, 41))
        self.pkg_Tip.setObjectName("pkgChoiceTips")
        self.pkgComboBox = QComboBox(self)
        # TODO 控件宽度改变
        self.pkgComboBox.setGeometry(QtCore.QRect(0, 0, 261, 31))
        # self.pkgComboBox.setMinimumSize(QSize(250,31))
        self.pkgComboBox.setObjectName("pkgComboBoxView")
        self.pkgComboBox.setFont(getWRYHFontStyle())
        self.package_layout.addWidget(self.pkg_Tip)
        self.package_layout.addWidget(self.pkgComboBox)
        self.package_layout.addStretch()  # 添加可拉伸弹簧
        self.initPkgData()

    def _init_class_path_layout(self):
        self.action_start = QPushButton(self)
        self.action_start.setText("Start")
        self.action_start.setGeometry(QtCore.QRect(0, 0, 81, 31))
        self.action_start.setObjectName("act_start")
        self.action_start.setFont(getWRYHFontStyle())
        self.action_start.clicked.connect(lambda: self.invokePkgAction())
        self.activity_class_layout = QHBoxLayout(self)
        self.activityClassPath = QLineEdit(self)
        self.activityClassPath.setPlaceholderText("目标页面类文件路径")
        self.activityClassPath.setGeometry(QtCore.QRect(0, 0, 291, 31))
        self.activityClassPath.setObjectName("class_path")
        self.activityClassPath.setFont(getWRYHFontStyle(12))
        self.activity_class_layout.addWidget(self.action_start)
        self.activity_class_layout.addWidget(self.activityClassPath)
        self.activity_class_layout.addStretch()

    def _init_package_add_layout(self):
        self.package_edit_layout = QHBoxLayout(self)
        # self.package_edit_layout.alignment()
        self.btnAddNewPkg = QPushButton(self)
        self.btnAddNewPkg.setText("添加新包名:")
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

    def _init_actions_layout(self):
        """
        初始化应用行为操作按钮控件
        :return:
        """
        self.actionLayout = QHBoxLayout()
        self.action_clear = QPushButton(self)
        self.action_clear.setText("Clear")
        self.action_clear.setGeometry(QtCore.QRect(0, 0, 81, 31))
        self.action_clear.setObjectName("act_clear")
        self.action_clear.setFont(getWRYHFontStyle())
        self.action_clear.clicked.connect(lambda: self.invokePkgAction())

        self.action_uninstall = QPushButton(self)
        self.action_uninstall.setText("Uninstall")
        self.action_uninstall.setGeometry(QtCore.QRect(0, 0, 71, 31))
        self.action_uninstall.setObjectName("act_uninstall")
        self.action_uninstall.setFont(getWRYHFontStyle())
        self.action_uninstall.clicked.connect(lambda: self.invokePkgAction())

        self.action_stop = QPushButton(self)
        self.action_stop.setText("Stop")
        self.action_stop.setGeometry(QtCore.QRect(0, 0, 71, 31))
        self.action_stop.setObjectName("act_stop")
        self.action_stop.setFont(getWRYHFontStyle())
        self.action_stop.clicked.connect(lambda: self.invokePkgAction())

        self.actionLayout.addWidget(self.action_clear, alignment=Qt.AlignLeft)
        self.actionLayout.addWidget(self.action_uninstall, alignment=Qt.AlignLeft)
        self.actionLayout.addWidget(self.action_stop, alignment=Qt.AlignLeft)
        self.actionLayout.setSpacing(10)
        self.actionLayout.addStretch()

    def get_current_choose_pkg(self):
        return self.pkgComboBox.currentText()

    def invokePkgAction(self):
        # TODO 判断设备是否连接
        currentPkgName = self.get_current_choose_pkg()
        if currentPkgName:
            class_Path = "{0}/{1}".format(currentPkgName, self.activityClassPath.text())
            ADBTools.start_app_page(class_Path, None)

    def initPkgData(self):
        """
        从数据库初始化包名数据信息
        :return:
        """
        if not self.dbManager:
            z_logger.error('数据库连接异常，请重启应用.')
            return
        self.updatePkgComBox()

    def updatePkgComBox(self):
        """
        更新PkgComBox数据显示
        :return:
        """
        if not self.dbManager:
            return
        packages = self.dbManager.queryData(column=self.dbManager.COLUMN_NAME,
                                       table_name=self.dbManager.TABLE_PACKAGE)
        self.pkgComboBox.clear()
        for item in packages:
            if item:
                self.pkgComboBox.addItem(item[0])
        self.selected_pkg = self.pkgComboBox.currentText()

    def on_package_add(self):
        """
        点击包名添加按钮
        :return:
        """
        new_pkg = self.pkg_inputEditText.text()
        if not new_pkg:
            z_logger.error("请先添加有效包名 !!!")
            return
        # check is exist  TODO 优化，可以直接查 pkgComboBox
        sql = "SELECT NAME FROM PACKAGE WHERE NAME=\'{0}\'".format(new_pkg)
        result = self.dbManager.exec_sql(sql)
        if len(result) == 0:
            self.dbManager.insertPackageRow(new_pkg)
            self.updatePkgComBox()
            z_logger.debug("包名添加成功!")
        else:
            z_logger.error("此包名已存在，无需再次添加")

if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = DeviceInfoDetail()
    mainWin.show()
    sys.exit(app.exec_())