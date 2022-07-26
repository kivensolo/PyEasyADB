import sys

from PyQt5 import QtCore
from PyQt5.QtCore import QSize, pyqtSlot, Qt
from PyQt5.QtWidgets import QToolBar, QHBoxLayout, QLabel, QComboBox, QApplication

from logcat.log import z_logger
from ui.sql import DBManager
from ui.widget.Dialogs import NewConnectDialog
from utils.Tools import getWRYHFontStyle
from utils.UITools import IconTool
from utils.UiWidgts import AppPushButton, AppDeviceLabel


class AppToolBar(QToolBar):
    selected_pkg = ''

    """
    App工具栏
    """
    def __init__(self, context):
        super().__init__()
        self.dbManager = DBManager()
        self.pkgComboBox = None
        self.device_info_name = None
        self.total_pkgs = []

        self.mainWindowContext = context
        self.root_layout = QHBoxLayout()
        self.setLayout(self.root_layout)

        self.setContentsMargins(5, 5, 5, 5)
        self.setStyleSheet("QWidget{background-color:rgb(229,229,229);border:none}")
        self.initNewConnectBtn()
        self.addDeviceInfo()
        self.addPackageChoose()

    def addPackageChoose(self):
        pkg_Tip = AppDeviceLabel()
        pkg_Tip.setText('包名:')
        pkg_Tip.setGeometry(QtCore.QRect(0, 0, 111, 41))
        pkg_Tip.setObjectName("pkgChoiceTips")
        comboBox = QComboBox(self)
        # TODO 控件宽度改变
        # 设置下拉显示固定个数，超过个数，滚动显示
        comboBox.setMaxVisibleItems(7)
        # 宽度调整策略，按照内容最大宽度
        # comboBox.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContents)
        # comboBox.setGeometry(QtCore.QRect(0, 0, 261, 31))
        comboBox.setMinimumSize(QSize(250,31))
        comboBox.setObjectName("pkgComboBoxView")
        comboBox.setFont(getWRYHFontStyle())
        comboBox.setStyleSheet(
            "QComboBox QAbstractItemView::item { min-height: 60px; min-width: 60px;"
            "outline:0px;}"
            "QComboBox QAbstractItemView::item:selected{background-color: #25ACFF;}"
            "QComboBox QAbstractItemView::item:hover{background-color: #75CAFF;}"
        )
        self.pkgComboBox = comboBox
        self.addWidget(pkg_Tip)
        self.addWidget(self.pkgComboBox)
        self.initPkgData()

    def addDeviceInfo(self):
        """
        设备名称&版本等信息展示
        :return:
        """
        deviceImageView = QLabel(self)
        deviceImageView.setPixmap(IconTool.buildQPixmap("device.png"))
        deviceImageView.setAlignment(Qt.AlignLeft)
        _deviceInfoName = AppDeviceLabel()
        # _deviceInfoName.setText('测试数据模拟效果')
        _deviceInfoName.setObjectName("device_prop")
        _deviceInfoName.setStyleSheet("""
            background-color: #FFFFFF ;
            border-width: 1px;
            border-style: solid;
            border-color: #ADADAD;
            min-width: 400px;
            max-width: 400px;
        """)
        self.device_info_name = _deviceInfoName
        self.addWidget(deviceImageView)
        self.addWidget(self.device_info_name)

    def initNewConnectBtn(self):
        icon = IconTool.buildQIcon("new_connect.png")
        tool_item_add_new = AppPushButton("", self.show_new_device_dialog)
        tool_item_add_new.setIcon(icon)
        tool_item_add_new.setIconSize(QSize(30, 30))
        tool_item_add_new.setToolTip("新建连接")
        # tool_item_add_new.setFlat(True)  # 按钮扁平化,去掉按钮边框
        self.addWidget(tool_item_add_new)

    @pyqtSlot()
    def show_new_device_dialog(self):
        new_connect_dialog = NewConnectDialog(self, self.mainWindowContext.add_device)
        # new_connect_dialog.finishSignal.connect(self.on_new_device_added)
        new_connect_dialog.setWindowModality(Qt.ApplicationModal)
        new_connect_dialog.exec()

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
        更新设备信息
        :param ip: 设备ip
        :param isconnect: 当前设备是否已连接
        :return: None
        """
        self.current_ip = ip
        z_logger.debug("Update device info! isConenct? =" + str(isconnect))
        result, value_tuple = self.dbManager.get_device_prop_info(ip)
        if result and len(value_tuple) != 0:
            # 从数据库查询到数据
            if value_tuple[0] == '':
                if not isconnect:  # 未连接设备的情况下
                    self.device_info_name.setText("请先连接此设备")
                else:  # 已连接设备，但设备信息为空，通常是自动刷新后加入了已连接设备
                    z_logger.debug("[Update_Device] Current device is connected, but no device info!")
                    self.parent.adbTools.get_device_info(ip, self.on_device_prop_get_by_adb)
            else:
                z_logger.debug("[Update_Device] Get this device prop cache! data = [%s]" % value_tuple[0])
                self.device_info_name.setText(value_tuple[0])
        else:
            # 从数据库查询不到数据，通常是手动添加的未连接设备
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

    def get_current_choose_pkg(self):
        return self.pkgComboBox.currentText()


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = AppToolBar(None)
    mainWin.show()
    sys.exit(app.exec_())