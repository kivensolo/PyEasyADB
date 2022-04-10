import sys

from PyQt5 import QtCore
from PyQt5.QtCore import Qt, QSize, pyqtSlot
from PyQt5.QtGui import QFont
from PyQt5.QtWidgets import QWidget, QHBoxLayout, QVBoxLayout, QLabel, QComboBox, QApplication, QPushButton, QLineEdit, \
    QListWidget, QListWidgetItem

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

    def __init__(self, parent):
        super().__init__()
        self.dbManager = DBManager()
        self.parent = parent

        self.setAttribute(Qt.WA_StyledBackground)
        # self.setStyleSheet("background-color:#FFAAFF");
        self.setStyleSheet('''''')
        self.root_layout = QVBoxLayout()
        self.setLayout(self.root_layout)

        # 设备名称等信息
        self._init_device_info_layout()
        self._init_choose_pkg_layout()
        self._init_package_add_layout()
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
        # self.device_name_layout.addSpacing(10)  # 左侧空隙
        self.deviceImageView = QLabel(self)
        self.deviceImageView.setPixmap(IconTool.buildQPixmap("device.png"))
        self.deviceImageView.setAlignment(Qt.AlignLeft)
        self.device_info_name = AppDeviceLabel()
        # self.device_info_name.setText('测试数据模拟效果')
        self.device_info_name.setObjectName("device_prop")
        self.device_info_name.setStyleSheet("""
            background-color: #FFFFFF ;
            border-width: 2px;
            border-style: solid;
            border-color: #ADADAD;
        """)
        self.device_name_layout.addWidget(self.deviceImageView)
        self.device_name_layout.addWidget(self.device_info_name)
        self.device_name_layout.addStretch()

    def _init_choose_pkg_layout(self):
        """
        包名控件初始化
        :return:
        """
        # self.setStyleSheet("QComboBox { min-height: 30px; min-width: 180px; } "
        #                    "QComboBox QAbstractItemView::item { min-height: 30px; min-width: 60px; }")
        self.selected_pkg = ''
        self.package_layout = QHBoxLayout(self)
        self.package_layout.alignment()
        self.pkg_Tip = AppDeviceLabel()
        self.pkg_Tip.setText('目标App包名:')
        self.pkg_Tip.setGeometry(QtCore.QRect(0, 0, 111, 41))
        self.pkg_Tip.setObjectName("pkgChoiceTips")
        self.pkgComboBox = QComboBox(self)
        # TODO 控件宽度改变
        # 设置下拉显示固定个数，超过个数，滚动显示
        self.pkgComboBox.setMaxVisibleItems(7)
        # 宽度调整策略，按照内容最大宽度
        # self.pkgComboBox.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContents)
        # self.pkgComboBox.setGeometry(QtCore.QRect(0, 0, 261, 31))
        self.pkgComboBox.setMinimumSize(QSize(250,31))
        self.pkgComboBox.setObjectName("pkgComboBoxView")
        self.pkgComboBox.setFont(getWRYHFontStyle())
        self.pkgComboBox.setStyleSheet(
           "QComboBox QAbstractItemView::item { min-height: 60px; min-width: 60px;"
           "outline:0px;}"
           "QComboBox QAbstractItemView::item:selected{background-color: #25ACFF;}"
           "QComboBox QAbstractItemView::item:hover{background-color: #75CAFF;}"
        )

        # ComboBox与额外的View及数据源绑定
        # self.package_list_widget = QListWidget()
        # self.pkgComboBox.setModel(self.package_list_widget.model())
        # self.pkgComboBox.setView(self.package_list_widget)

        self.package_layout.addWidget(self.pkg_Tip)
        self.package_layout.addWidget(self.pkgComboBox)
        self.package_layout.addStretch()  # 添加可拉伸弹簧
        self.initPkgData()

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
        self.action_clear.clicked.connect(lambda: self.invokeCommonAction("pm clear", True))

        self.action_uninstall = QPushButton(self)
        self.action_uninstall.setText("Uninstall")
        self.action_uninstall.setGeometry(QtCore.QRect(0, 0, 71, 31))
        self.action_uninstall.setObjectName("act_uninstall")
        self.action_uninstall.setFont(getWRYHFontStyle())
        self.action_uninstall.clicked.connect(lambda: self.invokeCommonAction("uninstall"))

        self.action_stop = QPushButton(self)
        self.action_stop.setText("Stop")
        self.action_stop.setGeometry(QtCore.QRect(0, 0, 71, 31))
        self.action_stop.setObjectName("act_stop")
        self.action_stop.setFont(getWRYHFontStyle())
        self.action_stop.clicked.connect(lambda: self.invokeCommonAction("am force-stop", True))

        self.actionLayout.addWidget(self.action_clear, alignment=Qt.AlignLeft)
        self.actionLayout.addWidget(self.action_uninstall, alignment=Qt.AlignLeft)
        self.actionLayout.addWidget(self.action_stop, alignment=Qt.AlignLeft)
        self.actionLayout.setSpacing(10)
        self.actionLayout.addStretch()

    def get_current_choose_pkg(self):
        return self.pkgComboBox.currentText()

    def invokePkgAction(self):
        if not self.parent.is_current_device_connect():
            return
        currentPkgName = self.get_current_choose_pkg()
        if currentPkgName:
            class_Path = "{0}/{1}".format(currentPkgName, self.activityClassPath.text())
            self.parent.adbTools.start_app_page(self.current_ip, class_Path, self._onStartAppEnd)

    def invokeCommonAction(self, action, isShell=False):
        if not self.parent.is_current_device_connect():
            return
        currentPkgName = self.get_current_choose_pkg()
        if currentPkgName:
            self.parent.adbTools.do_common_action(self.current_ip, currentPkgName, action, self._onStartAppEnd, isShell)

    @pyqtSlot(list)
    def _onStartAppEnd(self, result):
        for line in result:
            if "Error:" in line:
                z_logger.error("操作错误:" + str(line))
                return
            if "Failure" in line:
                z_logger.info("操作失败:" + str(line))
                return
        z_logger.info("操作完毕")

    def initPkgData(self):
        """
        从数据库初始化包名数据信息
        :return:
        """
        if not self.dbManager:
            z_logger.error('数据库连接异常，请重启应用.')
            return
        self.total_pkgs = []
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
                # 原始数据添加模式 默认情况下，QStandardItemModel存储项目，QListView子类显示弹出列表。
                self.pkgComboBox.addItem(item[0])
                # self.total_pkgs.append(item[0])
                # # 设置新的模型和视图
                # item_view = ComboBoxItem(item[0])
                # item_view.closeSignal.connect(self.delete_pkg)
                # item_view.chooseSignal.connect(self.choose)
                # listwitem = QListWidgetItem(self.package_list_widget)
                # # 将自定义item_view设置为在给定项目中显示
                # self.package_list_widget.setItemWidget(listwitem, item_view)
                # 新模式存在的问题，pkgComboBox内容选择没更新到pkgComboBox中。
        self.selected_pkg = self.pkgComboBox.currentText()
        # self.selected_pkg = self.total_pkgs[0]

    def choose(self, data):
        self.pkgComboBox.setEditText(data)

    def delete_pkg(self, data):
        # 删除事件回调
        index = self.total_pkgs.index(data)
        self.package_list_widget.takeItem(index)
        # self.total_pkgs.remove(data)
        del self.total_pkgs[index]

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
            z_logger.info("包名添加成功:" + new_pkg)
        else:
            z_logger.info("此包名已存在，您无需再次添加!")

    def update_device_info(self, ip, isconnect):
        """
        :param ip:
        :param isconnect: 当前设备是否已连接
        :return:
        """
        self.current_ip = ip
        z_logger.debug("Update device info! conenct=" + str(isconnect))
        result, value_tuple = self.parent.dbManager.get_device_prop_info(ip)
        if result and len(value_tuple) != 0:
            if value_tuple[0] == '' and not isconnect:
                # 未连接设备的情况下，从数据库中成功查询为空
                self.device_info_name.setText("请先连接此设备")
            else:
                z_logger.debug("[Update_Device] Get this device prop cache! data = [%s]" % value_tuple[0])
                self.device_info_name.setText(value_tuple[0])
        else:
            if isconnect:
                z_logger.debug("No this device prop cache, get with adb!")
                self.parent.adbTools.get_device_info(ip, self.on_device_prop_get_by_adb)
            else:
                self.device_info_name.setText("请先连接此设备")

    def on_device_prop_get_by_adb(self, result_list):
        """
        从ADB获取到设备属性数据
        :param result_list:
        :return:
        """
        # z_logger.debug("result_list="+str(result_list))
        manufacturer = ''
        model = ''
        sys_version = ''
        api_level = ''
        if len(result_list):
            if len(result_list) < 4:
                self.device_info_name.setText("Unknow Device")
            else:
                # 会存在['']的情况
                for line in result_list:
                    if 'android.os.Build.MANUFACTURER' in line:
                        manufacturer = self.get_prop_value(line)
                    if 'ro.product.model' in line:
                        model = self.get_prop_value(line)
                    if 'ro.build.version.release' in line:
                        sys_version = self.get_prop_value(line)
                    if 'ro.build.version.sdk' in line:
                        api_level = self.get_prop_value(line)
                result = "{0} {1} Android {2},API {3}".format(manufacturer, model, sys_version, api_level)
                z_logger.debug("result="+result)
                self.device_info_name.setText(result)
                self.parent.dbManager.update_device_prop(result, self.current_ip.split(":")[0])
        else:
            self.device_info_name.setText("Unknow Device")

    @staticmethod
    def get_prop_value(content):
        strArr = content.split(":")
        if len(strArr) == 2:
            return strArr[1].replace("[", "").replace("]", "").strip()
        return ''


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = DeviceInfoDetail(None)
    mainWin.show()
    sys.exit(app.exec_())