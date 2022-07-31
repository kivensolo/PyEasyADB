#!/usr/bin/python
# -*- coding: utf-8 -*-
import logging
import sys

from PyQt5 import QtCore
from PyQt5.QtCore import Qt, QSize
from PyQt5.QtGui import QTextCursor, QIcon
from PyQt5.QtWidgets import QTabWidget, QTabBar, QApplication, QMainWindow, QWidget, QComboBox, QTextBrowser, QSplitter, \
    QAction, QPushButton, QVBoxLayout, QHBoxLayout, QLabel

from logcat.log import z_logger
from ui.sql import DBManager
from utils.Tools import getWRYHFontStyle
from utils.UITools import IconTool
from utils.UiWidgts import AppDeviceLabel
from utils.Utils import Utils


class ButtomTabWidget(QTabWidget):
    """
    底部TabWidget控件
    """
    def __init__(self, parent=None):
        super(ButtomTabWidget, self).__init__(parent)
        # 日志组件
        self.consoleView = ConsoleWindow()
        z_logger.add_gui_log_handler(self.consoleView)
        self.tabBar = QTabBar()

        self.dbManager = DBManager()
        self.pkgComboBox = None
        self.device_info_name = None
        self.total_pkgs = []
        self.init_ui()

    def init_ui(self):
        self.tabBar.tabBarClicked.connect(self.status)
        self.tabBar.setExpanding(False)
        self.setTabBar(self.tabBar)
        # 将日志view添加至TabWidget中
        self.addTab(self.consoleView, IconTool.buildQIcon("logcat.png"), "Logcat")
        # self.consoleView.setVisible(False)
        # self.setFixedHeight(Utils.getItemHeight())
        self.consoleView.setVisible(True)
        self.setMaximumHeight(Utils.getWindowHeight())
        self.setTabPosition(QTabWidget.South)
        self.setStyleSheet(
            "QTabBar::tab {"
            "border: none; height: " + str(Utils.getItemHeight()) + "px; width:100px;"
            "color:black;"
            "} "
            "QTabBar::tab:selected { "
            "border: none;"
            "background: lightgray; "
            "} "
        )

    def status(self):
        # 槽函数
        if self.tabBar.tabText(self.tabBar.currentIndex()) == 'Logcat':
            if self.consoleView.isVisible():
                self.consoleView.setVisible(False)
                self.preHeight = self.width()
                self.setFixedHeight(Utils.getItemHeight())
            else:
                self.consoleView.setVisible(True)
                self.setMaximumHeight(Utils.getWindowHeight())

    def updateCurrentDeviceInfo(self, ip, isconnect):
        self.consoleView.updateSelectDeviceInfo(ip, isconnect)

    def get_fun_widget(self):
        return self.consoleView.get_fun_widget()


class ConsoleWindow(QMainWindow):
    """
    Desc: 底部应用日志输出窗口
    """
    global textEdit

    def __init__(self, parent=None):
        super(ConsoleWindow, self).__init__(parent)
        self.leftWiget = QWidget()
        self.functionTabWiget = None

        self.isConUrl = False
        self.setStyleSheet('''
            QPushButton{
                border: none;
                background-color: #0000 ;
            }
            
            QPushButton:hover {
                border: 1px solid #C0C0C0;
                border-radius:2px;
                background-color:#C0C0C0;  
                border-style: solid;
            }
            ''')
        self.functionTabWiget = InfoBarWidget()

        # self.combo = QComboBox(self)
        # self.combo.insertItem(0, 'Error')
        # self.combo.insertItem(1, 'Debug')
        # self.combo.insertItem(2, 'Verbose')
        # self.combo.insertItem(3, 'Warning')
        # self.combo.setCurrentIndex(0)

        self.initLeftFunctionWidget()

        # 右
        # 日志窗口控件初始化
        self.textEdit = QTextBrowser()
        self.textEdit.setOpenLinks(True)
        self.textEdit.setOpenExternalLinks(True)
        self.textEdit.setReadOnly(True)
        self.textEdit.unsetCursor()

        self.rightWiget = QWidget()
        self.rightWiget.setAutoFillBackground(True)
        self.rightWiget.setFixedWidth(15)

        self.lineTowSplitter = QSplitter(Qt.Horizontal)
        self.lineTowSplitter.addWidget(self.leftWiget)
        self.lineTowSplitter.addWidget(self.textEdit)
        self.lineTowSplitter.addWidget(self.rightWiget)

        self.mainSplitter = QSplitter(Qt.Vertical)
        self.mainSplitter.addWidget(self.functionTabWiget)
        self.mainSplitter.addWidget(self.lineTowSplitter)
        self.setCentralWidget(self.mainSplitter)

        # 重定向输出
        # sys.stdout = ConsoleEmittor(textWritten=self.normalOutputWritten)
        # sys.stderr = ConsoleEmittor(textWritten=self.normalOutputWritten)

    def initLeftFunctionWidget(self):
        """
        初始化左侧功能区
        :return:
        """
        clearButton = QPushButton(self)
        icon = QIcon(IconTool.buildQIcon("clear.png"))
        clearButton.setIcon(icon)
        clearButton.setFixedWidth(18)
        clearButton.setFixedHeight(20)
        clearButton.clicked.connect(self._clear)
        clearButton.setToolTip("Clear the logcat")
        layout = QVBoxLayout()
        layout.setAlignment(Qt.AlignTop)
        layout.setSpacing(1)
        layout.addWidget(clearButton)
        layout.setContentsMargins(4, 0, 0, 0)
        self.leftWiget.setAutoFillBackground(True)
        self.leftWiget.setLayout(layout)
        self.leftWiget.setFixedWidth(22)

    def normalOutputWritten(self, text):
        cursor = self.textEdit.textCursor()
        cursor.movePosition(QTextCursor.End)
        cursor.insertHtml(text)
        self.textEdit.setTextCursor(cursor)
        self.textEdit.ensureCursorVisible()

    def initMenuBar(self):
        menuBar = self.menuBar()
        fileMenu = menuBar.addMenu('Logcat')
        showLogAction = QAction('Show Log', self)
        fileMenu.addAction(showLogAction)

        helpMenu = menuBar.addMenu('Setting')
        aboutAction = QAction(IconTool.buildQIcon('setting.png'), 'About', self)
        helpMenu.addAction(aboutAction)

    def append_line(self, level, msg):
        self.textEdit.moveCursor(QTextCursor.End)
        content = self.check_link_addr(msg)
        log = "{0}: {1} <br />".format(self._buildStandardTime(), content)
        if level >= logging.ERROR:
            log = "<font color=\"red\">{0}</font>".format(log)
        elif level == logging.WARNING:
            log = "<font color=\"yellow\">{0}</font>".format(log)
        self.textEdit.insertHtml(log)

    def _clear(self):
        self.textEdit.clear()
        return

    def _buildStandardTime(self):
        import time
        ct = time.time()
        local_time = time.localtime(ct)
        data_head = time.strftime("%Y-%m-%d %H:%M:%S", local_time)
        data_secs = (ct - int(ct)) * 1000
        time_stamp = "%s.%03d" % (data_head, data_secs)
        return time_stamp

    def check_link_addr(self, text):
        if isinstance(text, str):
            import re
            regexUrl = re.compile(r"http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*,]|(?:%[0-9a-fA-F][0-9a-fA-F]))+",
                                  re.IGNORECASE)
            urls = regexUrl.findall(text)
            for url in urls:
                preS = "<a href=\"" + url + "\">" + url + "</a>"
                text = text.replace(url, preS)
        return text

    def updateSelectDeviceInfo(self, ip, isconnect):
        self.functionTabWiget.update_device_info(ip, isconnect)

    def get_fun_widget(self):
        # FIXME 如何直接找到子view
        return self.functionTabWiget


class InfoBarWidget(QWidget):
    """
    设备信息和包名选择的组合控件
    """
    def __init__(self):
        super().__init__()
        self.dbManager = DBManager()
        self.pkgComboBox = None
        self.device_info_name = None
        self.total_pkgs = []

        self.setAutoFillBackground(True)
        self.setStyleSheet(
            """
            background-color:rgb(244,244,244);
            """)
        self.layout = QHBoxLayout()
        self.layout.setAlignment(Qt.AlignLeft)
        self.layout.setSpacing(10)
        self.setLayout(self.layout)

        self.addDeviceInfo()
        self.addPackageChoose()

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
        self.layout.addWidget(deviceImageView)
        self.layout.addWidget(self.device_info_name)

    def addPackageChoose(self):
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
        self.layout.addWidget(self.pkgComboBox)
        self.initPkgData()

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

    def on_package_add(self, pkgName):
        """
        点击包名添加按钮
        :return:
        """
        if not pkgName:
            z_logger.error("请先添加有效包名 !!!")
            return False
        # TODO 对包名进行有效性判断
        # check is exist  TODO 优化，可以直接查 pkgComboBox
        sql = "SELECT NAME FROM PACKAGE WHERE NAME=\'{0}\'".format(pkgName)
        result = self.dbManager.exec_sql(sql)
        if len(result) == 0:
            self.dbManager.insertPackageRow(pkgName)
            self.updatePkgComBox()
            z_logger.info("包名添加成功:" + pkgName)
            return True
        else:
            z_logger.info("此包名已存在，您无需再次添加!")
            return False

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
    mainWin = ButtomTabWidget()
    mainWin.show()
    # mainWin = ConsoleWindow()
    # mainWin.show()
    # mainWin = InfoBarWidget()
    # mainWin.show()
    sys.exit(app.exec_())
