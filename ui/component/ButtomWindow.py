#!/usr/bin/python
# -*- coding: utf-8 -*-
import logging
import sys

from PyQt5 import QtCore
from PyQt5.QtCore import Qt, QSize
from PyQt5.QtGui import QTextCursor, QIcon
from PyQt5.QtWidgets import QTabWidget, QTabBar, QApplication, QMainWindow, QWidget, QComboBox, QTextBrowser, QSplitter, \
    QAction, QPushButton, QVBoxLayout, QHBoxLayout, QLabel, QListView
from qtpy import QtWidgets

from logcat.log import z_logger
from utils.ADBTools import ADBTools
from utils.PackageManager import PackageManager
from utils.Tools import getSongFontStyle
from utils.UITools import IconTool
from utils.Utils import Utils

adb_tool = ADBTools()


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
                "border: none; height: " + str(Utils.getItemHeight()) +
                "px; width:100px;"
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

    def updateSelectDeviceInfo(self, ip, isconnect):
        self.consoleView.updateSelectDeviceInfo(ip, isconnect)

    def get_fun_widget(self):
        return self.consoleView.get_fun_widget()


def changeLogColor(level, log):
    _color_log = log
    if level >= logging.ERROR:
        _color_log = "<font color=\"red\">{0}</font>".format(log)
    elif level == logging.WARNING:
        _color_log = "<font color=\"yellow\">{0}</font>".format(log)
    else:
        if "adb " in log:
            _color_log = "<font color=\"#005ac7\" >{0}</font>".format(log)
    return _color_log


def _build_time_stamp():
    import time
    ct = time.time()
    local_time = time.localtime(ct)
    data_head = time.strftime("%Y-%m-%d %H:%M:%S", local_time)
    data_secs = (ct - int(ct)) * 1000
    time_stamp = "%s.%03d" % (data_head, data_secs)
    return time_stamp + ": "


def check_link_addr(text):
    if isinstance(text, str):
        import re
        regexUrl = re.compile(r"http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*,]|(?:%[0-9a-fA-F][0-9a-fA-F]))+",
                              re.IGNORECASE)
        urls = regexUrl.findall(text)
        for url in urls:
            preS = "<a href=\"" + url + "\">" + url + "</a>"
            text = text.replace(url, preS)
    return text


class ConsoleWindow(QMainWindow):
    """
    Desc: 底部应用日志输出窗口
    """
    global terminalTextBrowser

    def __init__(self, parent=None):
        super(ConsoleWindow, self).__init__(parent)
        self.infoBarWidget = InfoBarWidget()

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

        self.leftWiget = QWidget()
        self.initLeftFunctionWidget()

        # 日志窗口控件初始化
        self.terminalTextBrowser = QTextBrowser()
        self.terminalTextBrowser.setOpenLinks(True)
        self.terminalTextBrowser.setOpenExternalLinks(True)
        self.terminalTextBrowser.setReadOnly(True)
        self.terminalTextBrowser.unsetCursor()

        self.rightWiget = QWidget()
        self.rightWiget.setAutoFillBackground(True)
        self.rightWiget.setFixedWidth(15)

        # 左侧工具栏+中间文本展示+右侧工具栏的分割器
        self.bodySplitter = QSplitter(Qt.Horizontal)
        self.bodySplitter.addWidget(self.leftWiget)
        self.bodySplitter.addWidget(self.terminalTextBrowser)
        self.bodySplitter.addWidget(self.rightWiget)

        self.verticalSplitter = QSplitter(Qt.Vertical)
        self.verticalSplitter.addWidget(self.infoBarWidget)
        self.verticalSplitter.addWidget(self.bodySplitter)
        self.verticalSplitter.setChildrenCollapsible(0)
        self.setCentralWidget(self.verticalSplitter)

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
        cursor = self.terminalTextBrowser.textCursor()
        cursor.movePosition(QTextCursor.End)
        cursor.insertHtml(text)
        self.terminalTextBrowser.setTextCursor(cursor)
        self.terminalTextBrowser.ensureCursorVisible()

    def initMenuBar(self):
        menuBar = self.menuBar()
        fileMenu = menuBar.addMenu('Logcat')
        showLogAction = QAction('Show Log', self)
        fileMenu.addAction(showLogAction)

        helpMenu = menuBar.addMenu('Setting')
        aboutAction = QAction(IconTool.buildQIcon('setting.png'), 'About', self)
        helpMenu.addAction(aboutAction)

    def append_log(self, logMsg, record: logging.LogRecord):
        level = record.levelno
        _funcName = record.funcName     # 执行log打印的函数名

        # 先清除log中结尾的换行符，因为后续会自己加
        logMsg = logMsg.rstrip("\n")
        # if "\n" in str(msg):
        #     # 检查内容有换行时(一般是输出内容)，则在最前面增加一个换行符，保证输出的缩进一致;
        #     msg = "\n{0}".format(msg)

        content = check_link_addr(logMsg)
        ui_log = changeLogColor(level, content)
        if _funcName != "on_adb_cmd_exectued":
            # 不是命令行执行的日志输出，都加上时间前缀
            ui_log = "{0}{1}".format(_build_time_stamp(), ui_log)
        self.terminalTextBrowser.append(ui_log)

        # 解决该控件插入Html时，不支持\n的问题
        # log = str(log).replace("\n", "<br>")
        # 文字后加换行符，准备下一次输出(注意必须要有一个空格，否则不生效)
        # log = log + "<br />"
        # self.textEdit.insertHtml(log)
        # 光标移动, 将输出内容全部顶出来
        # self.textEdit.moveCursor(QTextCursor.End)

    def _clear(self):
        self.terminalTextBrowser.clear()
        return

    def _scrollToBottom(self):
        current_pos = self.terminalTextBrowser.scrollToAnchor()
        return

    def updateSelectDeviceInfo(self, ip, isconnect):
        self.infoBarWidget.update_device_info(ip, isconnect)

    def get_fun_widget(self):
        # FIXME 如何直接找到子view
        return self.infoBarWidget


class InfoBarWidget(QWidget):
    """
    设备信息和包名选择的组合控件
    """

    def __init__(self):
        super().__init__()
        self.current_ip = ""
        self.pkgManger = PackageManager()
        self.pkgComboBox = QComboBox()
        # 设备名称
        self.device_info_desc = None
        # 目标应用包名  TODO 改为所选设备的运行时进程
        self.total_pkgs = []
        self.adbTools = ADBTools()

        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Minimum)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.sizePolicy().hasHeightForWidth())
        self.setSizePolicy(sizePolicy)
        self.setMaximumSize(QtCore.QSize(16777215, 55))

        self.qh_layout = QHBoxLayout(self)
        self.qh_layout.setContentsMargins(0, -1, -1, -1)
        self.qh_layout.setObjectName("info_bar_horizontalLayout")

        self.initDeviceInfo()
        self.initProcessComboBox()
        # 右侧添加补位弹簧
        spacerItem = QtWidgets.QSpacerItem(40, 20, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Minimum)
        self.qh_layout.addItem(spacerItem)
        self.qh_layout.setStretch(1, 1)
        self.qh_layout.setStretch(2, 1)
        self.qh_layout.setStretch(3, 1)

    def initDeviceInfo(self):
        """
        设备名称&版本等信息展示
        :return:
        """
        deviceImageView = QLabel(self)
        # deviceImageView.setPixmap(QPixmap("../../res/img/device.png"))
        deviceImageView.setPixmap(IconTool.buildQPixmap("device.png"))
        deviceImageView.setAlignment(Qt.AlignCenter)
        self.qh_layout.addWidget(deviceImageView)

        self.device_info_desc = QLabel()
        # self.device_info_desc.setText("B869Ajiojioajiojdq2165465461654")
        self.device_info_desc.setFont(getSongFontStyle())
        self.device_info_desc.setTextFormat(QtCore.Qt.AutoText)
        self.device_info_desc.setObjectName("device_prop")
        self.device_info_desc.setToolTip("设备名称信息")
        self.device_info_desc.setMinimumSize(QtCore.QSize(200, 30))
        self.device_info_desc.setMaximumSize(QtCore.QSize(500, 40))
        self.device_info_desc.setStyleSheet("""
                background-color: #f0f0f0 ;
                border: 1px solid #C0C0C0;
                padding: 2px,2px,2px,2px;
                margin: 0px,0px,20px,0px;
            """)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Maximum)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.device_info_desc.sizePolicy().hasHeightForWidth())
        self.device_info_desc.setSizePolicy(sizePolicy)
        self.qh_layout.addWidget(self.device_info_desc)

    def initProcessComboBox(self):
        """
        初始化进程列表展示Box
        :return:
        """
        comboBox = QComboBox()
        # 设置下拉显示固定个数，超过个数，滚动显示
        comboBox.setMaxVisibleItems(8)
        # 宽度调整策略，按照内容最大宽度
        # comboBox.setSizeAdjustPolicy(QComboBox.SizeAdjustPolicy.AdjustToContents)
        # comboBox.setGeometry(QtCore.QRect(0, 0, 261, 31))
        comboBox.setMinimumSize(QSize(250, 31))
        comboBox.setMaximumSize(QtCore.QSize(500, 40))
        comboBox.setObjectName("pkgComboBoxView")
        comboBox.setFont(getSongFontStyle())
        comboBox.setStyleSheet(
            "QComboBox QAbstractItemView::item { border-bottom:1px solid #d0d0d0;}"
            "QComboBox QAbstractItemView::item:selected{background-color: #2a89f6;}"
        )
        # Sets the view to be used in the combobox popup to the given itemView.
        comboBox.setView(QListView())
        comboBox.currentIndexChanged.connect(self.onPackageSelectedChanged)
        self.pkgComboBox = comboBox
        self.qh_layout.addWidget(self.pkgComboBox)
        self.init_process_info()

    def onPackageSelectedChanged(self):
        self.pkgManger.setSelectedPackage(self.pkgComboBox.currentText())

    def init_process_info(self):
        if len(self.current_ip) == 0:
            z_logger.error('未选择设备')
            return
        self.update_process_com_box()

    def update_process_com_box(self):
        """
        更新PkgComBox数据显示
        :return:
        """
        if len(self.current_ip) == 0:
            return
        adb_tool.get_running_process(self._onProcessFiltered)

    def _onProcessFiltered(self, sorted_processes):
        """
        针对已选设备查询到的满足条件的进程数据
        """
        self.pkgComboBox.clear()
        for user, pid, p_name in sorted_processes:
            self.pkgComboBox.addItem(f"{p_name}({pid})")

        # 第一条数据的p_name字段
        self.pkgManger.setSelectedPackage(sorted_processes[0][2])

    def update_device_info(self, ip, isconnect):
        """
        更新设备信息及进程数据
        :param ip: 设备ip
        :param isconnect: 当前设备是否已连接
        :return: None
        """
        self.current_ip = ip
        z_logger.debug("Update selected device info! conenct: " + str(isconnect))
        result, value_tuple = self.pkgManger.queryDeviceInfo(ip)
        if result and len(value_tuple) != 0:
            # 从数据库查询到数据
            if value_tuple[0] == '':
                if not isconnect:  # 未连接设备的情况下
                    self.device_info_desc.setText("请先连接此设备")
                else:  # 已连接设备，但设备信息为空，通常是自动刷新后加入了已连接设备
                    z_logger.debug("[Update_Device] Current device is connected, but no device info!")
                    self.adbTools.get_device_info(ip, self.on_device_prop_get_by_adb)
            else:
                z_logger.debug("[Update_Device] Get this device prop cache! data = [%s]" % value_tuple[0])
                self.device_info_desc.setText(value_tuple[0])
                if isconnect:
                    self.update_process_com_box()
                    # 更新设备后，立刻查询此设备的ps进程信息（TODO 做定时缓存）
        else:
            # 从数据库查询不到数据，通常是手动添加的未连接设备
            if isconnect:
                z_logger.debug("No this device prop cache, get with adb!")
                self.adbTools.get_device_info(ip, self.on_device_prop_get_by_adb)
            else:
                self.device_info_desc.setText("请先连接此设备")

    def on_device_prop_get_by_adb(self, result):
        """
        从ADB获取到设备属性数据
        :param result: adb返回的字符串
        :return:
        """
        # z_logger.debug("result_list="+str(result_list))
        result_list = result.split('\n')
        manufacturer = ''
        model = ''
        sys_version = ''
        api_level = ''
        if len(result_list):
            if len(result_list) < 4:
                self.device_info_desc.setText("Unknow Device")
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
                result = "{0} {1}({2}),API {3}".format(manufacturer, model, sys_version, api_level)
                z_logger.debug("result=" + result)
                self.device_info_desc.setText(result)
                self.pkgManger.updateDeviceInfo(result, self.current_ip.split(":")[0])
        else:
            self.device_info_desc.setText("Unknow Device")

    @staticmethod
    def get_prop_value(content):
        strArr = content.split(":")
        if len(strArr) == 2:
            return strArr[1].replace("[", "").replace("]", "").strip()
        return ''


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = ButtomTabWidget()
    mainWin.show()
    # mainWin = ConsoleWindow()
    # mainWin.show()
    # mainWin = InfoBarWidget()
    # mainWin.show()
    sys.exit(app.exec_())
