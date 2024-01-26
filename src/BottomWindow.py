#!/usr/bin/python
# -*- coding: utf-8 -*-
import logging
import sys

from PyQt5 import QtCore, QtWidgets
from PyQt5.QtCore import Qt, QSize
from PyQt5.QtGui import QTextCursor, QIcon
from PyQt5.QtWidgets import QTabWidget, QTabBar, QApplication, QMainWindow, QWidget, QComboBox, QTextBrowser, QSplitter, \
    QPushButton, QVBoxLayout, QHBoxLayout, QLabel, QListView, QCheckBox

from src import MainWindow
from src.logcat import log
from src.logcat.log import z_logger
from src.widget.CustomWidgets import LiveLogTextBrowser
from utils.ADBTools import ADBTools, LiveLogAdbThread
from utils.PackageManager import PackageManager
from utils.Tools import getSongFontStyle, getSimpleFontStyle
from utils.UITools import IconTool
from utils.Utils import Utils, LogUtils, _nameToLevel, _filterOptions

adb_tool = ADBTools()


class BottomTabWidget(QTabWidget):
    """
    底部TabWidget控件
    """
    def __init__(self, parent:MainWindow = None):
        super().__init__()
        self.mainwindow = parent

        self.tabBar = QTabBar()
        # 应用自身log输出组件
        self.consoleView = ConsoleWindow(parent)
        z_logger.add_gui_log_handler(self.consoleView)
        # LivingLogcat
        self.liveLogView = LogCatWindow(parent)

        # 当前选中的tab标签序号记录
        self.currentSelectedTabIndex = 0

        self.pkgComboBox = None
        self.device_info_name = None
        self.init_ui()

    def init_ui(self):
        self.tabBar.tabBarClicked.connect(self.on_tab_clicked)
        self.tabBar.currentChanged.connect(self.on_tab_toggle)
        self.tabBar.setExpanding(False)
        self.setTabBar(self.tabBar)

        # 选项卡绘制在页面下方
        self.setTabPosition(QTabWidget.South)

        # 添加组件至TabWidget中
        self.addTab(self.consoleView, IconTool.buildQIcon("console.png", "icons"), "Console")
        self.addTab(self.liveLogView, IconTool.buildQIcon("logcat.png"), "Logcat")

        # self.setFixedHeight(Utils.getItemHeight())
        self.setMaximumHeight(Utils.getWindowHeight())

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

    def on_tab_clicked(self, clickedIndex):
        """
        tabBar被点击时候的回调.
        注意：此时self.tabBar.currentIndex()的值，
        并不是被点击的标签页index，而是选中的页面的index，比如从A切到B, 当前的index依旧是A的。
        :param clickedIndex: 被点击的index
        :return:
        """
        _widgetView = self.currentWidget()
        if self.currentSelectedTabIndex != clickedIndex:
            if _widgetView.isVisible():
                # 不做手动处理，交给TabBar做正常的Tab切换逻辑。
                return

        # 手动进行控件的隐藏和现实
        if _widgetView.isVisible():
            _widgetView.setVisible(False)
            self.preHeight = self.width()
            self.setFixedHeight(Utils.getItemHeight())
        else:
            _widgetView.setVisible(True)
            self.setMaximumHeight(Utils.getWindowHeight())

    def on_tab_toggle(self):
        _index = self.tabBar.currentIndex()
        self.currentSelectedTabIndex = _index
        selectTabName = self.tabBar.tabText(_index)
        # if selectTabName == "Logcat":
        #     # 启动日志输出
        #     if self.livelogThread.isStoped:
        #         self.livelogThread.start()

    def updateSelectDeviceInfo(self, ip, isconnect):
        """
        更新所选设备的设备信息
        :return:
        """
        self.liveLogView.updateSelectDeviceInfo(ip, isconnect)
        self.consoleView.updateSelectDeviceInfo(ip, isconnect)

    def updateRunningProcessInfo(self):
        """
        更新运行时进程数据信息
        :return:
        """
        self.liveLogView.infoBarWidget.update_process_com_box()

    def clearRunningProcessComBox(self):
        """
        更新运行时进程数据信息
        :return:
        """
        self.liveLogView.infoBarWidget.update_process_com_box(False)

    def get_fun_widget(self):
        return self.consoleView.get_fun_widget()



class ConsoleWindow(QMainWindow):
    """
    Desc: 底部应用日志输出窗口
    """
    global terminalTextBrowser

    def initLeftFunctionWidget(self):
        """
        初始化左侧功能区
        :return:
        """
        clearButton = QPushButton(self)
        icon = QIcon(IconTool.buildQIcon("ic_clear.png", "icons"))
        clearButton.setIcon(icon)
        clearButton.setFixedWidth(24)
        clearButton.setFixedHeight(28)
        clearButton.clicked.connect(self._clear)
        clearButton.setToolTip("Clear the console log")

        scrollBtn = QPushButton(self)
        icon = QIcon(IconTool.buildQIcon("ic_arrow_down.png", "icons"))
        scrollBtn.setIcon(icon)
        scrollBtn.setFixedWidth(24)
        scrollBtn.setFixedHeight(28)
        scrollBtn.clicked.connect(self._scrollToBottom)
        scrollBtn.setToolTip("Scroll to bottom")

        layout = QVBoxLayout()
        layout.setAlignment(Qt.AlignTop)
        layout.setSpacing(5)
        layout.addWidget(clearButton)
        layout.addWidget(scrollBtn)
        layout.setContentsMargins(4, 0, 0, 0)
        self.leftWiget.setAutoFillBackground(True)
        self.leftWiget.setLayout(layout)
        self.leftWiget.setFixedWidth(27)

    def __init__(self, parent:MainWindow):
        super().__init__(parent)
        self.infoBarWidget = InfoBarWidget(parent)

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

    def normalOutputWritten(self, text):
        cursor = self.terminalTextBrowser.textCursor()
        cursor.movePosition(QTextCursor.End)
        cursor.insertHtml(text)
        self.terminalTextBrowser.setTextCursor(cursor)
        self.terminalTextBrowser.ensureCursorVisible()

    def append_log(self, logMsg, record: logging.LogRecord):
        level = record.levelno
        # _funcName = record.funcName     # 执行log打印的函数名

        # 先清除log中结尾的换行符，因为后续会自己加
        logMsg = logMsg.rstrip("\n")
        is_need_appen_prefix = logMsg.startswith(log.TIME_STAMP_PREFIX)
        if is_need_appen_prefix:
            logMsg = logMsg[7:]  # 切片操作，去除前缀

        # url检测
        content = LogUtils.highlight_link_addr(logMsg)
        # 颜色检测
        ui_log = LogUtils.changeLogColor(is_need_appen_prefix, level, content)

        if is_need_appen_prefix:
            ui_log = f"{LogUtils.build_time_stamp()}{ui_log}"
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
        self.terminalTextBrowser.moveCursor(QTextCursor.End)
        self.terminalTextBrowser.ensureCursorVisible()

    def updateSelectDeviceInfo(self, ip, isconnect):
        self.infoBarWidget.update_device_info(ip, isconnect)

    def get_fun_widget(self):
        # FIXME 如何直接找到子view
        return self.infoBarWidget


class LogCatWindow(QMainWindow):
    """
    实时ADB log窗口
    """
    def __init__(self, parent: MainWindow):
        super().__init__()
        self.mainWindow = parent
        self.livelogThread = LiveLogAdbThread()
        self.livelogThread.live_log_dump_signal.connect(self.on_live_log_dump)
        self.logTextBrowser = LiveLogTextBrowser()

        #信息栏
        self.infoBarWidget = self.LogcatInfoBarWidget(self, parent)
        #左侧功能区
        self.leftWiget = self.LeftBarWidget(self)

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

        # 日志窗口控件初始化

        self.logTextBrowser.setOpenLinks(True)
        self.logTextBrowser.setOpenExternalLinks(True)
        self.logTextBrowser.setReadOnly(True)
        self.logTextBrowser.unsetCursor()

        self.rightWiget = QWidget()
        self.rightWiget.setAutoFillBackground(True)
        self.rightWiget.setFixedWidth(15)

        # 左侧工具栏+中间文本展示+右侧工具栏的分割器
        self.vSplitter = QSplitter(Qt.Horizontal)
        self.vSplitter.addWidget(self.leftWiget)
        self.vSplitter.addWidget(self.logTextBrowser)
        self.vSplitter.addWidget(self.rightWiget)

        # 设置垂直方向的控件区域
        self.verticalSplitter = QSplitter(Qt.Vertical)
        self.verticalSplitter.addWidget(self.infoBarWidget)
        self.verticalSplitter.addWidget(self.vSplitter)
        self.verticalSplitter.setChildrenCollapsible(0)
        self.setCentralWidget(self.verticalSplitter)

    def normalOutputWritten(self, text):
        cursor = self.logTextBrowser.textCursor()
        cursor.movePosition(QTextCursor.End)
        cursor.insertHtml(text)
        self.logTextBrowser.setTextCursor(cursor)
        self.logTextBrowser.ensureCursorVisible()

    def on_live_log_dump(self, ui_log):
        if self._isOverLimitRow():
            self.logTextBrowser.clear()
            self.livelogThread.clearFilter()
        self.logTextBrowser.append(ui_log)

    def _isOverLimitRow(self):
        # 数据是否超长 TODO 做设置处理
        return self.logTextBrowser.document().lineCount() > 2000

    def clear(self):
        self.logTextBrowser.clear()
        return

    def reloadHistoryLiveLog(self):
        """
        重新从历史数据中筛出目标应用和等级的数据
        调用点: 应用进程变化、日志级别变化、进程开关
        :return:
        """
        logs = self.livelogThread.getHistoryLogsWithRules()
        self.logTextBrowser.clear()
        if logs is not None:
            if len(logs) > 0:
                self.logTextBrowser.append(logs)

    def start_or_stop(self):
        if self.mainWindow is None or self.mainWindow.current_device_addr == "":
            z_logger.error('请先连接设备！！')
        else:
            addr = self.mainWindow.current_device_addr
            cmd = f'adb -s {addr} logcat -v time'
            self.livelogThread.cmd = cmd
            if not self.livelogThread.isRunning:
                self.leftWiget.changeStartButton(True)
                self.livelogThread.start()
            else:
                self.leftWiget.changeStartButton(False)
                self.livelogThread.stop()
        return

    def _scrollToBottom(self):
        self.logTextBrowser.moveCursor(QTextCursor.End)
        self.logTextBrowser.ensureCursorVisible()

    def updateSelectDeviceInfo(self, ip, isconnect):
        self.infoBarWidget.update_device_info(ip, isconnect)

    def get_fun_widget(self):
        # FIXME 如何直接找到子view
        return self.infoBarWidget

    class LeftBarWidget(QWidget):
        """
        左侧功能区
        :return:
        """
        def __init__(self, parent):
            super().__init__()
            # parent为LogCatWindow
            self.parent = parent
            self.startButton = QPushButton()
            self.icon_start = QIcon(".\\res\\icons\\ic_start.png")
            self.icon_stop = QIcon(".\\res\\icons\\ic_stop.png")
            self.setUpUi()

        def setUpUi(self):
            self.startButton.setIcon(self.icon_start)
            self.startButton.setFixedWidth(24)
            self.startButton.setFixedHeight(28)
            self.startButton.clicked.connect(self.parent.start_or_stop)
            self.startButton.setToolTip("Start live logcat")

            clearButton = QPushButton(self)
            icon = QIcon(IconTool.buildQIcon("ic_clear.png", "icons"))
            clearButton.setIcon(icon)
            clearButton.setFixedWidth(24)
            clearButton.setFixedHeight(28)
            clearButton.clicked.connect(self.parent.clear)
            clearButton.setToolTip("Clear the logcat")

            scrollBtn = QPushButton(self)
            icon = QIcon(IconTool.buildQIcon("ic_arrow_down.png", "icons"))
            scrollBtn.setIcon(icon)
            scrollBtn.setFixedWidth(24)
            scrollBtn.setFixedHeight(28)
            scrollBtn.clicked.connect(self.parent._scrollToBottom)
            scrollBtn.setToolTip("Scroll to bottom")

            layout = QVBoxLayout()
            layout.setAlignment(Qt.AlignTop)
            layout.setSpacing(5)
            layout.addWidget(self.startButton)
            layout.addWidget(clearButton)
            layout.addWidget(scrollBtn)
            layout.setContentsMargins(4, 0, 0, 0)
            self.setAutoFillBackground(True)
            self.setLayout(layout)
            self.setFixedWidth(27)

        def changeStartButton(self, start: bool):
            if not start:
                ic_name = "ic_start.png"
                tips = "Start live logcat"
            else:
                ic_name = "ic_stop.png"
                tips = "Stop"
            icon = IconTool.buildQIcon(ic_name, "icons")
            self.startButton.setIcon(icon)
            self.startButton.setToolTip(tips)

    class LogcatInfoBarWidget(QWidget):
        # 日志筛选级别改变事件
        # filterLevelChangeSignal = pyqtSignal(str)

        """
        设备信息和包名选择的组合控件
        """
        _isOnlyShowSelectedApp = False

        def __init__(self, parent, mainWindow: MainWindow):
            super().__init__()
            self.parentView = parent
            self.mainwindow = mainWindow
            self.current_ip = ""
            self.setStyleSheet("""
                background-color: #ffffff ;
                
                QComboBox {
                       border: 2px solid #c4c4c4;
                       border-radius: 4px;
                }
                QComboBox:selected {
                       border: 2px solid #2a89f6;
                       border-radius: 4px;
                }
                QComboBox::drop-down{
                       width:15px;
                   }
                QComboBox QAbstractItemView::item { border-bottom:1px solid #d0d0d0;}
                QComboBox QAbstractItemView::item:selected{background-color: #2a89f6;}
            """)
            self.pkgManger = PackageManager()

            self.pkgComboBox = QComboBox()
            self.logLevelComboBox = QComboBox()
            self.filterCheckBox = QComboBox()

            # 设备名称
            self.device_info_desc = None
            self.adbTools = ADBTools()

            sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Maximum)
            sizePolicy.setHorizontalStretch(0)
            sizePolicy.setVerticalStretch(0)
            sizePolicy.setHeightForWidth(self.sizePolicy().hasHeightForWidth())
            self.setSizePolicy(sizePolicy)
            self.setMaximumHeight(37)

            self.qh_layout = QHBoxLayout(self)
            self.qh_layout.setContentsMargins(0, 0, 0, 0)
            self.qh_layout.setObjectName("info_bar_horizontalLayout")

            self.initDeviceInfo()
            self.initProcessComboBox()
            self.initLogLevelComboBox()
            spacerItem = QtWidgets.QSpacerItem(40, 20, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Minimum)
            self.initFilterOptionComBox()

            self.qh_layout.addWidget(self.device_info_desc)
            self.qh_layout.addWidget(self.pkgComboBox)
            self.qh_layout.addWidget(self.logLevelComboBox)
            self.qh_layout.addItem(spacerItem)  # 右侧添加补位弹簧
            self.qh_layout.addWidget(self.filterCheckBox)

            self.qh_layout.setStretch(1, 2)
            self.qh_layout.setStretch(2, 2)
            self.qh_layout.setStretch(3, 3)
            self.qh_layout.setStretch(4, 2)

        def initFilterOptionComBox(self):
            """
            初始化后侧过滤选择的CheckBox
            :return:
            """
            comboBox = QComboBox()
            # 反射将QComboBox的wheelEvent方法重置掉
            setattr(comboBox, "wheelEvent", lambda a: None)
            comboBox.setMaxVisibleItems(6)
            comboBox.setMinimumSize(QSize(300, 31))
            comboBox.setMaximumSize(QSize(500, 40))
            comboBox.setObjectName("filterOptionsComBox")
            comboBox.setFont(getSimpleFontStyle(size=12))
            comboBox.setView(QListView())
            for name in _filterOptions:
                comboBox.addItem(name)
            comboBox.currentIndexChanged.connect(self._onPidFilterChanged)

            self.filterCheckBox = comboBox

        def _onPidFilterChanged(self):
            """
            pid进程过滤规则改变
            :return:
            """
            filterOption = self.filterCheckBox.currentText()
            enableFilterPid = False
            if filterOption == _filterOptions[0]:
                enableFilterPid = True
            self.parentView.livelogThread.logcatFilter.setOnlyShowSelectedPidLog(enableFilterPid)
            self.parentView.reloadHistoryLiveLog()

        def initDeviceInfo(self):
            """
            设备名称&版本等信息展示
            :return:
            """
            deviceImageView = QLabel()
            deviceImageView.setPixmap(IconTool.buildQPixmap("device_small.png"))
            deviceImageView.setStyleSheet("""
                background-color: #00ff00; 
            """)
            self.qh_layout.addWidget(deviceImageView)

            self.device_info_desc = QLabel()
            self.device_info_desc.setObjectName("device_prop")
            self.device_info_desc.setToolTip("设备名称信息")
            self.device_info_desc.setMinimumWidth(200)
            self.device_info_desc.setMaximumWidth(350)
            self.device_info_desc.setStyleSheet("""
                background-color: #ff0000; 
                border: 1px solid #d7d7d7;
            """)
            sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
            sizePolicy.setHorizontalStretch(0)
            sizePolicy.setVerticalStretch(0)
            self.device_info_desc.setSizePolicy(sizePolicy)

        def initProcessComboBox(self):
            """
            初始化进程列表展示Box
            :return:
            """
            comboBox = QComboBox()
            # 反射将QComboBox的wheelEvent方法重置掉
            setattr(comboBox, "wheelEvent", lambda a: None)
            # 设置下拉显示固定个数，超过个数，滚动显示
            comboBox.setMaxVisibleItems(8)
            comboBox.setStyleSheet("QComboBox QAbstractItemView { min-width: 700px; }")
            comboBox.setMinimumSize(QSize(250, 31))
            comboBox.setMaximumSize(QtCore.QSize(350, 40))
            comboBox.setObjectName("pkgComboBoxView")
            comboBox.setFont(getSimpleFontStyle(size=12))
            # Sets the view to be used in the combobox popup to the given itemView.
            comboBox.setView(QListView())
            comboBox.currentIndexChanged.connect(self.onPackageSelectedChanged)
            self.pkgComboBox = comboBox
            self.init_process_info()

        def onPackageSelectedChanged(self):
            applicationInfo = self.pkgComboBox.currentText()
            self.pkgManger.setSelectedRunningProcessInfo(applicationInfo)
            if self._isOnlyShowSelectedApp:
                z_logger.debug(f"所选进程被改变:{applicationInfo}, 刷新日志输出.")
                pid = self.pkgManger.currentSelectedRunningProcessPid
                self.parentView.livelogThread.logcatFilter.onSelectedPidChanged(pid)
                self.parentView.reloadHistoryLiveLog()

        def init_process_info(self):
            if len(self.current_ip) == 0:
                z_logger.error('未选择设备')
                return
            self.update_process_com_box()

        def update_process_com_box(self, isConnect=True):
            """
            更新PkgComBox数据显示
            :return:
            """
            if len(self.current_ip) == 0:
                return
            if not isConnect:
                z_logger.debug("当前选中设备离线,清空runningProcess数据")
                self.pkgComboBox.clear()
                self.pkgManger.setSelectedRunningProcessInfo("")
                return
            state = adb_tool.get_running_process(self.current_ip, self._onProcessFiltered)
            if not state:
                self.mainwindow.check_device_status()

        def _onProcessFiltered(self, sorted_processes):
            """
            针对已选设备查询到的满足条件的进程数据
            """
            if len(sorted_processes) == 0:
                z_logger.error("获取设备进程异常！")
                return
            self.pkgComboBox.clear()
            for user, pid, p_name in sorted_processes:
                self.pkgComboBox.addItem(f"{p_name}({pid})")

            # 第一条数据的p_name字段
            self.pkgManger.setSelectedRunningProcessInfo(f'{sorted_processes[0][2]}({sorted_processes[0][1]})')

        def initLogLevelComboBox(self):
            """
            初始化日志过滤级别展示Box
            :return:
            """
            comboBox = QComboBox()
            # 设置下拉显示固定个数，超过个数，滚动显示
            comboBox.setMaxVisibleItems(6)
            comboBox.setMinimumSize(QSize(120, 31))
            comboBox.setMaximumSize(QSize(120, 40))
            comboBox.setObjectName("logLevelComboBox")
            comboBox.setFont(getSimpleFontStyle(size=11))
            # Sets the view to be used in the combobox popup to the given itemView.
            comboBox.setView(QListView())
            for name in _nameToLevel:
                comboBox.addItem(name)
            comboBox.currentIndexChanged.connect(self.onLogLevelSelectedChanged)
            self.logLevelComboBox = comboBox
            self.init_process_info()

        def onLogLevelSelectedChanged(self):
            levelText = self.logLevelComboBox.currentText()
            z_logger.debug(f"设置日志过滤级别为: {levelText}")

            self.parentView.livelogThread.logcatFilter.changeFilterLevelByName(levelText)
            self.parentView.reloadHistoryLiveLog()

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
                    self.update_process_com_box(isconnect)
            else:
                # 从数据库查询不到数据，通常是手动添加的未连接设备
                if isconnect:
                    z_logger.debug("No this device prop cache, get with adb!")
                    self.adbTools.get_device_info(ip, self.on_device_prop_get_by_adb)
                else:
                    self.device_info_desc.setText("请先连接此设备")
                    self.update_process_com_box(False)

        def on_device_prop_get_by_adb(self, result):
            """
            从ADB获取到设备属性数据(只有新增设备时，才会去查属性)
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
                    z_logger.info_with_stamp("设备概况信息:" + result)
                    self.device_info_desc.setText(result)
                    self.pkgManger.updateDeviceInfo(result, self.current_ip.split(":")[0])
                    z_logger.debug("Get running processes...")
                    self.update_process_com_box()

            else:
                self.device_info_desc.setText("Unknow Device")

        @staticmethod
        def get_prop_value(content):
            strArr = content.split(":")
            if len(strArr) == 2:
                return strArr[1].replace("[", "").replace("]", "").strip()
            return ''



class InfoBarWidget(QWidget):
    """
    设备信息和包名选择的组合控件
    """

    def __init__(self, parent: MainWindow):
        super().__init__()
        self.mainwindow = parent
        self.current_ip = ""
        self.pkgManger = PackageManager()
        self.pkgComboBox = QComboBox()
        # 设备名称
        self.device_info_desc = None
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
        deviceImageView.setPixmap(IconTool.buildQPixmap("Honeyview_device.png"))
        deviceImageView.setAlignment(Qt.AlignCenter)
        self.qh_layout.addWidget(deviceImageView)

        self.device_info_desc = QLabel()
        # self.device_info_desc.setText("B869Ajiojioajiojdq2165465461654")
        self.device_info_desc.setFont(getSongFontStyle())
        self.device_info_desc.setTextFormat(QtCore.Qt.AutoText)
        self.device_info_desc.setObjectName("device_prop")
        self.device_info_desc.setToolTip("设备名称信息")
        self.device_info_desc.setMinimumSize(QtCore.QSize(200, 30))
        self.device_info_desc.setMaximumSize(QtCore.QSize(350, 40))
        self.device_info_desc.setStyleSheet("""
                background-color: #f0f0f0 ;
                border: 1px solid #C0C0C0;
                margin: 0px,0px,20px,0px;
            """)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Maximum)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.device_info_desc.sizePolicy().hasHeightForWidth())
        self.device_info_desc.setSizePolicy(sizePolicy)
        self.qh_layout.addWidget(self.device_info_desc)

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
        else:
            # 从数据库查询不到数据，通常是手动添加的未连接设备
            if isconnect:
                z_logger.debug("No this device prop cache, get with adb!")
                self.adbTools.get_device_info(ip, self.on_device_prop_get_by_adb)
            else:
                self.device_info_desc.setText("请先连接此设备")

    def on_device_prop_get_by_adb(self, result):
        """
        从ADB获取到设备属性数据(只有新增设备时，才会去查属性)
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
                z_logger.info_with_stamp("设备概况信息:" + result)
                self.device_info_desc.setText(result)
                self.pkgManger.updateDeviceInfo(result, self.current_ip.split(":")[0])
                z_logger.debug("Get running processes...")

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
    mainWin = BottomTabWidget(None)
    mainWin.show()
    # mainWin = ConsoleWindow()
    # mainWin.show()
    # mainWin = InfoBarWidget()
    # mainWin.show()
    sys.exit(app.exec_())
