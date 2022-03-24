from PyQt5.QtCore import QSize, QVersionNumber, Qt, QT_VERSION_STR, pyqtSlot, QModelIndex, QTimer
from PyQt5 import QtCore, QtWidgets
from PyQt5.QtGui import QIcon, QFont, QPixmap, QColor, QStandardItemModel, QStandardItem
from PyQt5.QtWidgets import QMessageBox, QApplication, QPushButton, QComboBox, QLabel, QMenuBar, QMenu, QAction, \
    QStatusBar, QToolTip, qApp, QTextEdit, QLineEdit, QVBoxLayout, QGroupBox, QGridLayout, QHBoxLayout, QMainWindow, \
    QToolBar, QSplitter, QTreeView, QAbstractItemView, QWidget

from config.settings import PATH_LOGO_ICON, APP_VERSION, DB_NAME
from logcat import log
from ui import DevicePage
from ui.DeviceGroup import OldUIManager
from ui.sql import DBManager
from ui.widget.ButtomConsoleWindow import ButtomWindow
from ui.widget.NewConnectDialog import NewConnectDialog
from utils.CmdExecutor import CmdExecutor
from utils.UITools import IconTool
from utils.UiWidgts import AppPushButton
from utils.Utils import Utils

from ui.BaseWindow import BaseWindow

def initBtnTips():
    # 这种静态的方法设置一个用于显示工具提示的字体。这里使用10px滑体字体。
    QToolTip.setFont(QFont('SansSerif', 10))


def get_cmd_executor(finish_callback):
    """
     获取命令执行对象
    :param finish_callback: 命令执行完毕的回调函数
    :return: cmd执行者对象实例
    """
    executor = CmdExecutor()
    executor.setFinishCallback(finish_callback)
    return executor


class MainWindow(BaseWindow):
    tree_model = None
    # 本地缓存ip数据
    local_ip_List = []

    """
    QMainWindow 类提供了一个主要的应用程序窗口。
    用它可以让应用程序添加状态栏,工具栏和菜单栏。
    """
    def __init__(self):
        super().__init__()
        self.initWindow()

        # 初始化数据库帮助类
        self.dbManager = DBManager()

        # 主窗口分割器
        self.main_splitter = None
        # 内容显示的分割器
        self.content_splitter = None
        # 底部控制台窗口
        self.bottom_console_window = None
        # 左侧面板相关变量
        self.left_panel = None
        self.tree_view = None

        self.center_panel = None
        self.right_panel = None

        self.currentCmd = 'adb devices'
        self.active_ip_list = []

        self.init_all_ui()
        self.show()

    def init_all_ui(self):
        # 初始化菜单栏
        self.init_menu_bar()
        self.init_toolbar()
        # 初始化功能区
        # self.init_test_func_group()

        self.init_left_panel()
        self.init_center_panel()

        # 初始化底部状态栏
        self.init_status_bar()

        # 初始化底部控件
        self.bottom_console_window = ButtomWindow()

        # 将各组件组合
        self.content_splitter = QSplitter(Qt.Horizontal)
        self.content_splitter.setHandleWidth(0)  # thing to grab the splitter
        self.content_splitter.addWidget(self.left_panel)
        self.content_splitter.addWidget(self.center_panel)
        self.content_splitter.setStretchFactor(0, 3)
        self.content_splitter.setStretchFactor(1, 5)
        self.content_splitter.setChildrenCollapsible(0)  # 过窄不可隐藏子控件
        self.main_splitter = QSplitter(Qt.Vertical)
        self.main_splitter.setHandleWidth(0)
        self.main_splitter.addWidget(self.content_splitter)
        self.main_splitter.addWidget(self.bottom_console_window)
        self.main_splitter.setStretchFactor(0, 5)
        self.main_splitter.setStretchFactor(1, 4)
        self.main_splitter.setChildrenCollapsible(0)  # 过窄不可隐藏子控件
        self.setCentralWidget(self.main_splitter)

        initBtnTips()
        # 连接信号槽 Signals & slots. PyQt5的事件机制 http://code.py40.com/2004.html
        QtCore.QMetaObject.connectSlotsByName(self)

    def closeEvent(self, event):
        #  关闭窗口的时候,触发QCloseEvent。重写closeEvent()事件处理程序
        reply = QMessageBox.question(self, '提示', "要离开了么?",
                                     QMessageBox.Yes | QMessageBox.No,
                                     QMessageBox.No)
        if reply == QMessageBox.Yes:
            event.accept()
        else:
            event.ignore()

    def init_toolbar(self):
        toolbar = QToolBar(self)
        toolbar.setContentsMargins(5, 5, 5, 5)
        toolbar.setStyleSheet("QWidget{background-color:rgb(229,229,229);border:none}")
        icon = IconTool.buildQIcon("new_connect.png")
        tool_item_add_new = AppPushButton(toolbar, self.add_new_device)
        tool_item_add_new.setStyleSheet("QPushButton:pressed{background-color:rgb(206,220,232)}")
        tool_item_add_new.setIcon(icon)
        tool_item_add_new.setStatusTip("新建连接")
        tool_item_add_new.setIconSize(QSize(24, 20))
        # tool_item_add_new.setFlat(True)  # 按钮扁平化,去掉按钮边框

        # self.tool_item_add_new.setGeometry(QtCore.QRect(0, 0, 120, 120))
        toolbar.addWidget(tool_item_add_new)
        self.addToolBar(toolbar)

    @pyqtSlot()
    def add_new_device(self):
        print("add new device\n")
        new_connect_dialog = NewConnectDialog(self)
        # new_connect_dialog.finishSignal.connect(self.on_new_device_added)
        new_connect_dialog.setWindowModality(Qt.ApplicationModal)
        new_connect_dialog.exec()

    def on_new_device_added(self):
        pass


    # def onExecConnect(self, url):
    #     print("onExecConnect ")
    #     self.statusBar().showMessage(url)

    def init_center_panel(self):
        self.center_panel = QWidget()
        layout = QVBoxLayout()
        # left, top, right, bottom
        layout.setContentsMargins(5, 5, 5, 5)
        # centralwidget = QWidget(self)
        # centralwidget.setGeometry(QtCore.QRect(221, 70, 500, 400))
        # 创建设备信息 的多分页窗口
        self.stacked_device_info = QtWidgets.QStackedWidget(self)  # QStackedWidget表示多分页的窗口
        self.stacked_device_info.setObjectName("stackedWidget_param")
        self.stacked_device_info.setGeometry(221, 70, 500, 400)
        # self.stackedWidget_param.setStyleSheet("QWidget{background-color:rgb(188,188,188);border:none}")
        # 创建分页对象，并载入分页
        file_page = DevicePage.DeviceA_Area()
        common_page = DevicePage.DeviceB_Area()
        advance_page = DevicePage.DeviceA_Area()
        self.stacked_device_info.addWidget(file_page)
        self.stacked_device_info.addWidget(common_page)
        self.stacked_device_info.addWidget(advance_page)
        self.stacked_device_info.setCurrentIndex(0) #切换至选中页

        # OldUIManager(self, centralwidget).initViews()
        layout.addWidget(self.stacked_device_info)
        self.center_panel.setLayout(layout)

    def init_left_panel(self):
        """
        初始化左侧面板
        :return: None
        """
        layout = QVBoxLayout()
        self.left_panel = QWidget()
        layout.setContentsMargins(0, 0, 6, 0)  # left, top, right, bottom
        # 创建tree_view
        self.tree_view = QTreeView()
        layout.addWidget(self.tree_view)
        self.left_panel.setLayout(layout)

        # TODO 需要先查一遍设备
        self.update_tree_view()
        # TODO 切换设备信息页面
        # tree_view.clicked.connect(self.getDebugData)

    def init_status_bar(self):
        statusbar = QStatusBar(self)
        statusbar.setObjectName("statusbar")
        statusbar.setStyleSheet("background-color:rgb(229,229,229)")
        self.setStatusBar(statusbar)

    def init_menu_bar(self):
        """
        进行顶部菜单及菜单行为初始化
        :return:
        """
        menubar = self.menuBar()
        menubar.setGeometry(QtCore.QRect(0, 0, 705, 23))
        menubar.setObjectName("menubar")
        _translate = QtCore.QCoreApplication.translate

        log.d('initMenuBar :: actions')
        # QAction可以操作菜单栏,工具栏,或自定义键盘快捷键
        act_close = QAction(self)
        act_close.setObjectName("act_close")
        act_close.setText(_translate("MainWindow", "Close"))

        act_exit = QAction(QIcon('./res/img/logo.png'), '&Exit', self)
        act_exit.setObjectName("exit_app")
        act_exit.setShortcut('Ctrl+Q')  # 自定义快捷键
        act_exit.setStatusTip('Exit application')  # 自定义提示
        act_exit.triggered.connect(qApp.quit)  # 建立信号连接槽
        act_exit.setText(_translate("MainWindow", "退出"))
        # act_close.setText("退出")

        # menu_actions = [act_close, act_exit]
        menus = [
            _translate("MainWindow", "菜单"),
            _translate("MainWindow", "编辑")
        ]
        actions = {
            menus[0]: [act_close, act_exit],  # 菜单1对应的action
            menus[1]: [act_exit]              # 菜单2对应的action
        }

        log.d('initMenuBars')
        # 初始化菜单项
        for menu in menus:
            menu_item = menubar.addMenu(menu)
            acts = actions[menu]
            for action in acts:
                menu_item.addAction(action)
            menubar.addAction(menu_item.menuAction())
        # 将menu添加到menubar上
        self.setMenuBar(menubar)

    def initWindow(self):
        self.resize(int(Utils.getWindowWidth()*0.618), int(Utils.getWindowHeight()*0.618))
        self.statusBar().showMessage('ready')
        super(MainWindow, self).initWindow()

    def setupUi(self):
        """ 初始化View  """
        # 基础Qt Widget

# ----------------------------------左侧TreeView START-----------------------------------------------
    def update_tree_view(self):
        """
        更新tree_view的数据样式
        :param tree_view:
        :return:
        TODO 优化，学习TreeView 只需要刷新数据，而不需要重新构建UI
        https://blog.csdn.net/qq_27061049/article/details/89641210
        """
        tree_view = self.tree_view
        self.tree_model = QStandardItemModel()
        device_item = QStandardItem("Devices")
        device_item.type = 'deviceRoot'
        device_item.removeRows(0, device_item.rowCount())
        other_item = QStandardItem("Other")
        self.local_ip_List.clear()
        all_device = self.dbManager.get_all_device()
        # TODO 自定义排序规则
        all_device.sort()
        for device in all_device:
            addr = device[0] + ":" + device[1]  # ip:port
            self.local_ip_List.append(addr)
            if addr in self.active_ip_list:
                qicon = IconTool.buildQIcon("logo.png")
            else:
                qicon = IconTool.buildQIcon("logo_gray.png")
            item = QStandardItem(qicon, addr)
            item.ip = addr
            item.type = "Device"
            device_item.appendRow(item)
        self.tree_model.appendColumn([device_item, other_item])
        # setHeaderData 要放在appendColumn之后
        self.tree_model.setHeaderData(0, Qt.Horizontal, '设备信息')
        tree_view.setContextMenuPolicy(Qt.CustomContextMenu)
        tree_view.setEditTriggers(QAbstractItemView.NoEditTriggers)
        # 数据绑定至UI
        tree_view.setModel(self.tree_model)
        # tree_view.customContextMenuRequested.connect(self.openContextMenu)
        tree_view.doubleClicked.connect(self.onTreeItemDoubleClicked)
        tree_view.clicked.connect(self.on_tree_item_clicked)

    @pyqtSlot(QModelIndex)
    def onTreeItemDoubleClicked(self, index):
        # 当树状item被点击时，可以通过获取item类型来处罚设备点击逻辑
        item = self.tree_model.itemFromIndex(index)  # QStandardItem
        if item.type == "Device":
            print("onTreeItemDoubleClicked %s" % item.ip)
        # self.connect_device(item.ip)

    @pyqtSlot(QModelIndex)
    def on_tree_item_clicked(self, index):
        # self.stackedWidget_param.setCurrentIndex(index)
        item = self.tree_model.itemFromIndex(index)
        if item.type == "Device":
            if item.ip == "192.10.20.1:5555":
                self.stacked_device_info.setCurrentIndex(1)
            elif item.ip == "172.31.10.236:5555":
                self.stacked_device_info.setCurrentIndex(0)
# ----------------------------------左侧TreeView End-----------------------------------------------

# ----------------------------------ADB 操作 START-----------------------------------------------
    def connect_device(self, addr):
        # timer = QTimer()
        # timer.start(500)
        self.currentCmd = "adb connect %s" % addr
        self._invoke_adb_cmd()

    @pyqtSlot()
    def check_device_status(self):
        self.currentCmd = 'adb devices'
        self._invoke_adb_cmd()

    def _invoke_adb_cmd(self):
        log.d("_invoke_adb_cmd")
        cmdExecutor = get_cmd_executor(self.on_adb_cmd_exectued)
        cmdExecutor.exec(self.currentCmd)

    def on_adb_cmd_exectued(self, result):
        # check current cmd
        # for r in result:
        #     print(r)
        log.d("on_adb_cmd_exectued")

        if "cmdExectuedTimeout" in result:
            print("cmdExectuedTimeout")
            return
        if self.currentCmd.startswith('adb connect'):
            self.check_device_status()
        elif self.currentCmd == 'adb devices':
            for r in result: # ['List of devices attached\r', '172.31.10.236:5555\tdevice\r', '\r', '']
                # 找到连接成功的设备
                print("devices=" + r)
                log.d("devices=" + r)
                if r in self.local_ip_List:
                    # 修改图标的颜色
                    self.active_ip_list.append(r)
                    # print("\n 当前ips：" + self.active_ip_list)
            # 更新设备状态
# ----------------------------------ADB 操作 END-----------------------------------------------


def dpiAuto():
    """
    hight-dip适配
    Qt从5.6.0开始，支持High-DPI
    :return: None
    """
    v_compare = QVersionNumber(5, 6, 0)
    v_current, _ = QVersionNumber.fromString(QT_VERSION_STR)
    if QVersionNumber.compare(v_current, v_compare) >= 0:
        QApplication.setAttribute(Qt.AA_EnableHighDpiScaling)
