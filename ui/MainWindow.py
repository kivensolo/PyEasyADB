from PyQt5.QtCore import QSize, QVersionNumber, Qt, QT_VERSION_STR, pyqtSlot, QModelIndex, QTimer, pyqtSignal
from PyQt5 import QtCore, QtWidgets
from PyQt5.QtGui import QIcon, QFont, QPixmap, QColor, QStandardItemModel, QStandardItem, QCursor
from PyQt5.QtWidgets import QMessageBox, QApplication, QPushButton, QComboBox, QLabel, QMenuBar, QMenu, QAction, \
    QStatusBar, QToolTip, qApp, QTextEdit, QLineEdit, QVBoxLayout, QGroupBox, QGridLayout, QHBoxLayout, QMainWindow, \
    QToolBar, QSplitter, QTreeView, QAbstractItemView, QWidget, QTreeWidgetItem

from config.settings import PATH_LOGO_ICON, APP_VERSION, DB_NAME
from logcat import log
from logcat.log import z_logger
from ui import DevicePage
from ui.DeviceGroup import OldUIManager
from ui.DeviceInfoView import DeviceInfoDetail
from ui.sql import DBManager
from ui.widget.ButtomConsoleWindow import ButtomWindow
from ui.widget.NewConnectDialog import NewConnectDialog
from utils.ADBTools import ADBTools
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
        self.current_device_ip = ""

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

        self.active_ip_list = []

        self.init_all_ui()
        self.show()
        self.adbTools = ADBTools()

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
        file_page = DeviceInfoDetail()
        self.stacked_device_info.addWidget(file_page)
        self.stacked_device_info.setCurrentIndex(0)  # 切换至选中页

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
        self.init_tree_view()
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

        z_logger.debug('initMenuBar :: actions')
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

        z_logger.debug('initMenuBars')
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
    def init_tree_view(self):
        """
        更新tree_view的数据样式
        :param tree_view:
        :return:
        TODO 优化，学习TreeView 只需要刷新数据，而不需要重新构建UI
        https://blog.csdn.net/qq_27061049/article/details/89641210
        https://blog.csdn.net/seniorwizard/article/details/110199352?spm=1001.2101.3001.6650.8&utm_medium=distribute.pc_relevant.none-task-blog-2%7Edefault%7EBlogCommendFromBaidu%7ERate-8.pc_relevant_default&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2%7Edefault%7EBlogCommendFromBaidu%7ERate-8.pc_relevant_default&utm_relevant_index=13
        """
        # 表头信息
        self.tree_model = QStandardItemModel()

        device_item = QStandardItem("Devices")
        # self.tree_model.setItem(0, 1, device_item2)
        device_item.type = 'DeviceRoot'
        device_item.removeRows(0, device_item.rowCount())
        other_item = QStandardItem("Other")

        self.local_ip_List.clear()
        # 获取所有本地缓存ip数据
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
        # 添加列
        self.tree_model.appendColumn([device_item, other_item])
        # setHeaderData 要放在appendColumn之后
        self.tree_model.setHeaderData(0, Qt.Horizontal, '设备信息')
        self.tree_view.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree_view.customContextMenuRequested.connect(self.on_ip_menu_show) # 右键菜单显示函数
        self.tree_view.setEditTriggers(QAbstractItemView.NoEditTriggers)
        # 数据绑定至UI
        self.tree_view.setModel(self.tree_model)
        self.tree_view.doubleClicked.connect(self.onTreeItemDoubleClicked)
        self.tree_view.clicked.connect(self.on_tree_item_clicked)
        # 展开整个树形视图
        self.tree_view.expandAll()

        self.tree_view.contextMenu = QMenu()
        self.actionC = self.tree_view.contextMenu.addAction(IconTool.buildQIcon("logo.png"), '| 删除设备')
        # self.actionC.setShortcut('Ctrl+D')  #设置快捷键
        self.actionC.triggered.connect(self.actionRemove)#连接删除功能函数
        # self.actionC.setDisabled(True)
        self.action_disconnect = self.tree_view.contextMenu.addAction(IconTool.buildQIcon("logo.png"), '| 断开连接')
        # self.action_disconnect.setShortcut('Ctrl+S')  #设置快捷键
        self.action_disconnect.triggered.connect(self.actionRemove)#连接删除功能函数

    def on_ip_menu_show(self):
        self.tree_view.contextMenu.move(QCursor.pos())  # 移动到鼠标点击位置
        self.tree_view.contextMenu.show()

    def actionRemove(self):
        pass

    def del_tree_node(self):
        item_child = self.tree_view.currentItem()
        root = self.tree_view.invisibleRootItem()
        for item in self.tree_view.selectedItems():
            (item.parent() or root).removeChild(item_child)
        # 从数据库中移除

    def add_tree_node(self, ip):
        item_child = self.tree_view.currentItem()
        node = QStandardItem(IconTool.buildQIcon("logo_gray.png"), ip)
        node.ip = ip
        node.type = "Device"
        item_child.appendRow(node)

    @pyqtSlot(QModelIndex)
    def onTreeItemDoubleClicked(self, index):
        # 当树状item被点击时，可以通过获取item类型来处罚设备点击逻辑
        item = self.tree_model.itemFromIndex(index)  # QStandardItem
        if item.type == "Device":
            z_logger.debug("onTreeItemDoubleClicked %s" % item.ip)
            self.connect_device(item.ip)

    @pyqtSlot(QModelIndex)
    def on_tree_item_clicked(self, index):
        # self.stackedWidget_param.setCurrentIndex(index)
        item = self.tree_model.itemFromIndex(index)
        if item.type == 'DeviceRoot':
            # z_logger.debug('刷新设备状态')
            self.check_device_status()
        elif item.type == "Device":
            self.current_device_ip = item.ip
            # TODO 更新设备信息
# ----------------------------------左侧TreeView End-----------------------------------------------

# ----------------------------------ADB 操作 START-----------------------------------------------
    def connect_device(self, addr):
        self.adbTools.connect_device(addr, self.on_adb_cmd_exectued)

    @pyqtSlot()
    def check_device_status(self):
        self.adbTools.get_devices_state(self.on_adb_cmd_exectued)

    @pyqtSlot(list)
    def on_adb_cmd_exectued(self, result):
        z_logger.debug('On adb cmd exectued:' + str(result))
        if "cmdExectuedTimeout" in result:
            z_logger.info("Cmd exec time out!")
            return
        for item in result:
            if len(item) < 1:
                continue
            print(item)
            if self.adbTools.current_cmd.startswith('adb connect'):
                if item.startswith('already connected to'):
                    z_logger.debug("Already connected!")
                else:
                    # 可能会存在空的情况
                    z_logger.info("设备已连接")
                    self.check_device_status()
            elif self.adbTools.current_cmd == 'adb devices':
                self.check_devices_state(result)

    def check_devices_state(self, result):
        """
        检查ADb连接的设备状态
        :param result:
            [
                'List of devices attached\r',
                '172.31.10.236:5555\tdevice\r',
                '\r',
                ''
            ]
        :return:
        """
        for r in result:
            # TODO 优化数据检查
            if r in self.local_ip_List:
                z_logger.debug("devices=" + r)
                if not r:
                    self.active_ip_list.append(r)
                    z_logger.debug("active devices:" + r)
        if len(self.active_ip_list) > 0:
            self.current_device_ip = self.active_ip_list[0]
            z_logger.info("当前选中设备：" + self.current_device_ip)
        else:
            z_logger.info("当前无任何连接设备")


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
