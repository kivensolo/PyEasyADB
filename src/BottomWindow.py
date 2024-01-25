#!/usr/bin/python
# -*- coding: utf-8 -*-
import logging
import re
import sys

from PyQt5 import QtCore, QtWidgets
from PyQt5.QtCore import Qt, QSize
from PyQt5.QtGui import QTextCursor, QIcon, QPixmap
from PyQt5.QtWidgets import QTabWidget, QTabBar, QApplication, QMainWindow, QWidget, QComboBox, QTextBrowser, QSplitter, \
    QPushButton, QVBoxLayout, QHBoxLayout, QLabel, QListView, QCheckBox

from src import MainWindow
from src.logcat import log
from src.logcat.log import z_logger
from src.widget.CustomWidgets import LiveLogTextBrowser
from utils.ADBTools import ADBTools, LiveLogAdbThread
from utils.PackageManager import PackageManager
from utils.Tools import getSongFontStyle, getWRYHFontStyle, getSimpleFontStyle
from utils.UITools import IconTool
from utils.Utils import Utils

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
        self.addTab(self.consoleView, IconTool.buildQIcon("logcat.png"), "Console")
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


def changeLogColor(appen_prefix, level, log):
    _color_log = log

    if appen_prefix:                # 蓝
        _color_log = "<font color=\"#005ac7\" >{0}</font>".format(log)
        _color_log = str(_color_log).replace("\n", "<br>")
        return _color_log

    if level >= logging.ERROR:      # 红
        _color_log = "<font color=\"#bf360c\">{0}</font>".format(log)
    elif level == logging.WARNING:  # 黄
        _color_log = "<font color=\"#b6a014\">{0}</font>".format(log)
    elif level == logging.INFO:     # 黑
        _color_log = "<font color=\"#263238\" >{0}</font>".format(log)
    elif level == logging.DEBUG:    # 绿
        _color_log = "<font color=\"#388e3c\">{0}</font>".format(log)

    # 解决该控件插入Html时，不支持\n的问题
    _color_log = str(_color_log).replace("\n", "<br>")
    # 文字后加换行符，准备下一次输出(注意必须要有一个空格，否则不生效)
    # _color_log = _color_log + "<br />"
    return _color_log


def _build_time_stamp():
    import time
    ct = time.time()
    local_time = time.localtime(ct)
    data_head = time.strftime("%Y-%m-%d %H:%M:%S", local_time)
    data_secs = (ct - int(ct)) * 1000
    time_stamp = "%s.%03d" % (data_head, data_secs)
    return time_stamp + ": "


def highlight_link_addr(text):
    if isinstance(text, str):
        import re
        # FIXME 匹配  http://imgzm.qun7.com/uploads/20230117/63c66916d79e9.jpg!webp_____position:2   失败
        regexUrl = re.compile(r"http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*,]|(?:%[0-9a-fA-F][0-9a-fA-F]))+(?:\.jpg|\.jpeg|\.png|\.gif|\.bmp|\.webp)*",
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
        content = highlight_link_addr(logMsg)
        # 颜色检测
        ui_log = changeLogColor(is_need_appen_prefix, level, content)

        if is_need_appen_prefix:
            ui_log = f"{_build_time_stamp()}{ui_log}"
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


_nameToLevel = {
    'Verbose': logging.NOTSET,
    'Debug': logging.DEBUG,
    'Info': logging.INFO,
    'Warn': logging.WARNING,
    'Error': logging.ERROR,
    'Assert': logging.CRITICAL
}

_simpleNameToLevel = {
    "D": logging.DEBUG,
    "I": logging.INFO,
    "W": logging.WARNING,
    "E": logging.ERROR,
    "A": logging.CRITICAL
}


class LogCatWindow(QMainWindow):
    """
    实时ADB log窗口
    """
    def __init__(self, parent: MainWindow):
        super().__init__()
        self.mainWindow = parent
        self.logcatFilter = LogCatFilter()
        self.livelogThread = LiveLogAdbThread()
        self.livelogThread.output_received.connect(self.on_live_log_dump)

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
        self.logTextBrowser = LiveLogTextBrowser()
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

    def on_live_log_dump(self, content: list):
        src_log = content[1]
        logMsg = src_log.rstrip("\n")
        if len(logMsg) == 0:
            # 部分设备(例如S3)会在每条输出后输出\n,这种数据过滤掉
            return

        self.logcatFilter.record(src_log)
        isFiltered, pid, level = self.logcatFilter.filter(src_log)
        if isFiltered:
            # TODO 过滤后，刷新现有数据，支持从输出记录中进行筛选
            return

        if self._isOverLimitRow():
            self.logTextBrowser.clear()
            self.logcatFilter.clear()

        # url高亮处理
        content = highlight_link_addr(logMsg)
        # 着色处理
        ui_log = changeLogColor(False, level, content)

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
        logs = self.logcatFilter.getHistoryLogsWithRules()
        self.logTextBrowser.clear()
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
                self._changeStartButton(True)
                self.livelogThread.start()
            else:
                self._changeStartButton(False)
                self.livelogThread.stop()
                self.logcatFilter.clear()
        return

    def _changeStartButton(self, start: bool):
        if not start:
            ic_name = "ic_start.png"
            tips = "Start live logcat"
        else:
            ic_name = "ic_stop.png"
            tips = "Stop"
        icon = IconTool.buildQIcon(ic_name, "icons")
        self.startButton.setIcon(icon)
        self.startButton.setToolTip(tips)

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

    class LogcatInfoBarWidget(QWidget):
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
            """)
            self.pkgManger = PackageManager()

            self.pkgComboBox = QComboBox()
            self.logLevelComboBox = QComboBox()
            self.filterCheckBox = QCheckBox()

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
            # 右侧添加补位弹簧
            spacerItem = QtWidgets.QSpacerItem(40, 20, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Minimum)
            self.qh_layout.addItem(spacerItem)
            self.initFilterCheckBox()

            self.qh_layout.setStretch(1, 2)
            self.qh_layout.setStretch(2, 2)
            self.qh_layout.setStretch(3, 3)
            self.qh_layout.setStretch(4, 2)

        def initFilterCheckBox(self):
            """
            初始化后侧过滤选择的CheckBox
            :return:
            """
            self.filterCheckBox.setChecked(self._isOnlyShowSelectedApp)
            self.filterCheckBox.setText("Show only selected application")
            self.filterCheckBox.stateChanged.connect(self._onPidFilterToggled)
            self.filterCheckBox.setStyleSheet("""
                    border: 1px solid #C0C0C0;
                    padding: 2px,2px,2px,2px;
                    margin: 0px,0px,20px,0px;
                """)
            self.qh_layout.addWidget(self.filterCheckBox)

        def _onPidFilterToggled(self, state):
            """
            pid进程过滤规则改变
            :param state:
            :return:
            """
            isChecked = (state == Qt.Checked)
            self._isOnlyShowSelectedApp = isChecked
            self.parentView.logcatFilter.setOnlyShowSelectedPidLog(isChecked)
            # FIXME 数据量多了会卡UI
            self.parentView.reloadHistoryLiveLog()

        def initDeviceInfo(self):
            """
            设备名称&版本等信息展示
            :return:
            """
            deviceImageView = QLabel()
            deviceImageView.setPixmap(IconTool.buildQPixmap("Honeyview_device.png"))
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
            comboBox.setMaximumSize(QtCore.QSize(350, 40))
            comboBox.setObjectName("pkgComboBoxView")
            comboBox.setFont(getSongFontStyle())
            comboBox.setStyleSheet(
                """
                    QComboBox {
                       border: 2px solid #c4c4c4;
                       border-radius: 4px;
                    }
                    QComboBox:selected {
                       border: 2px solid #2a89f6;
                       border-radius: 4px;
                    }
                    QComboBox::drop-down{
                        background-color: none; 
                         width:15px;
                    }
                    QComboBox QAbstractItemView { min-width: 700px; }
                    QComboBox QAbstractItemView::item { border-bottom:1px solid #d0d0d0;}
                    QComboBox QAbstractItemView::item:selected{background-color: #2a89f6;}
                """
            )
            # Sets the view to be used in the combobox popup to the given itemView.
            comboBox.setView(QListView())
            comboBox.currentIndexChanged.connect(self.onPackageSelectedChanged)
            self.pkgComboBox = comboBox
            self.qh_layout.addWidget(self.pkgComboBox)
            self.init_process_info()

        def onPackageSelectedChanged(self):
            applicationInfo = self.pkgComboBox.currentText()
            self.pkgManger.setSelectedRunningProcessInfo(applicationInfo)
            if self._isOnlyShowSelectedApp:
                z_logger.debug(f"所选进程被改变:{applicationInfo}, 刷新日志输出.")
                pid = self.pkgManger.currentSelectedRunningProcessPid
                self.parentView.logcatFilter.onSelectedPidChanged(pid)
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
            comboBox.setMinimumSize(QSize(100, 31))
            comboBox.setMaximumSize(QSize(100, 40))
            comboBox.setObjectName("logLevelComboBox")
            comboBox.setFont(getSimpleFontStyle())
            comboBox.setStyleSheet(
                """
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
                """
            )
            # Sets the view to be used in the combobox popup to the given itemView.
            comboBox.setView(QListView())
            for name in _nameToLevel:
                comboBox.addItem(name)
            comboBox.currentIndexChanged.connect(self.onLogLevelSelectedChanged)
            self.logLevelComboBox = comboBox
            self.qh_layout.addWidget(self.logLevelComboBox)
            self.init_process_info()

        def onLogLevelSelectedChanged(self):
            levelText = self.logLevelComboBox.currentText()
            z_logger.debug(f"设置日志过滤级别为: {levelText}")
            self.parentView.logcatFilter.changeFilterLevelByName(levelText)
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
                        self.update_device_info(ip, False)
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



class LogCatFilter(object):
    # 过滤的pid
    selected_pid = ""
    # 日志过滤级别(只显示 >= 此级别的日志) 默认ALL
    _filtered_level = logging.NOTSET
    _only_show_selected_app_log = False
    log_cache_list = []
    # 过滤后的gui历史log
    gui_history_log = []

    def __init__(self):
        pass

    def setOnlyShowSelectedPidLog(self, enable: bool):
        self._only_show_selected_app_log = enable

    def changeFilterLevelByName(self, levelName: str):
        """
        设置过滤级别tag
        :param levelName: I\W\E\D 等
        """
        if len(levelName) == 1:
            self._filtered_level = _simpleNameToLevel.get(levelName, logging.NOTSET)
        else:
            self._filtered_level = _nameToLevel.get(levelName, logging.NOTSET)

    def onSelectedPidChanged(self, pid=""):
        self.selected_pid = pid

    def filter(self, logMsg):
        """
        单条实时日志的过滤处理
        :param logMsg: 原始的单条日志数据
                 01-22 11:20:30.188 W/InputMethodManagerService( 1884): LogMessage
        :return:
        返回格式:
            <是否会被过滤(True|False)> <当前日志的进程pid> <当前日志的级别(数字)>
        """

        pattern  = re.compile(r'^.+\s([VIDWE])\/.+\(\s*(\d+)\)\:.+$')
        match = pattern.search(logMsg)
        if match:
            _level_name = match.group(1)
            _pid = match.group(2)
        else:
            _pid = "-1"
            _level_name = "I"

        _level = _simpleNameToLevel.get(_level_name, logging.NOTSET)

        # Level——1: 日志级别过滤
        if _level < self._filtered_level:
            return True, _pid, _level
            # TODO 如果需要过滤,则更新历史数据

        # Level——2: 进程过滤
        if self._only_show_selected_app_log:
            # 与选中进程不一致的进程需要被过滤掉
            isFilter = (_pid != self.selected_pid)
            return isFilter, _pid, _level
        else:
            return False, _pid, _level

    def record(self, _historyLog):
        """
        记录获取到的每一条日志数据
        :param _historyLog:
        :return:
        """
        # TODO 同步
        self.log_cache_list.append(_historyLog)

    def clear(self):
        self.gui_history_log.clear()
        self.log_cache_list.clear()

    def getHistoryLogsWithRules(self):
        """
        从缓存列表中，获取历史日志书
        :return: ，收集过滤后的数据
        """
        self.gui_history_log.clear()
        for _log in self.log_cache_list:
            filtered, pid, level = self.filter(_log)
            if not filtered:
                # url高亮处理
                content = highlight_link_addr(_log)
                # 着色处理
                ui_log = changeLogColor(False, level, content)
                self.gui_history_log.append(ui_log)
            else:
                continue

        return "\n".join(self.gui_history_log)


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
                    self.update_device_info(ip, False)
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
