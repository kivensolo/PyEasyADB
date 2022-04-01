from PyQt5.QtCore import QSize, QVersionNumber, Qt, QT_VERSION_STR, pyqtSlot, QModelIndex, QTimer, pyqtSignal
from PyQt5 import QtCore, QtWidgets
from PyQt5.QtGui import QIcon, QFont, QPixmap, QColor, QStandardItemModel, QStandardItem, QCursor
from PyQt5.QtWidgets import QMessageBox, QApplication, QPushButton, QComboBox, QLabel, QMenuBar, QMenu, QAction, \
    QStatusBar, QToolTip, qApp, QTextEdit, QLineEdit, QVBoxLayout, QGroupBox, QGridLayout, QHBoxLayout, QMainWindow, \
    QToolBar, QSplitter, QTreeView, QAbstractItemView, QWidget, QTreeWidgetItem, QListWidgetItem

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

TYPE_DEVICE = "Device"

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


def is_device_node(item: QStandardItem):
    """
    判断节点数据是否是设备节点
    :param item: 节点标准数据
    :return:
    """
    return item and item.type == TYPE_DEVICE


def is_device_active(state):
    """
    设备连接状态是否正常
    :param state:
    :return:
    """
    return state == 'device'


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

        self.icon_connect = IconTool.buildQIcon("state_connect.png")
        self.icon_disconnect = IconTool.buildQIcon("state_disconnect.png")

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

        # 已连接设备列表
        self.active_ip_list = []

        self.adbTools = ADBTools()
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
        tool_item_add_new = AppPushButton(toolbar, self.show_new_device_dialog)
        tool_item_add_new.setStyleSheet("QPushButton:pressed{background-color:rgb(206,220,232)}")
        tool_item_add_new.setIcon(icon)
        tool_item_add_new.setStatusTip("新建连接")
        tool_item_add_new.setIconSize(QSize(24, 20))
        # tool_item_add_new.setFlat(True)  # 按钮扁平化,去掉按钮边框

        # self.tool_item_add_new.setGeometry(QtCore.QRect(0, 0, 120, 120))
        toolbar.addWidget(tool_item_add_new)
        self.addToolBar(toolbar)

    @pyqtSlot()
    def show_new_device_dialog(self):
        new_connect_dialog = NewConnectDialog(self, self.add_device)
        # new_connect_dialog.finishSignal.connect(self.on_new_device_added)
        new_connect_dialog.setWindowModality(Qt.ApplicationModal)
        new_connect_dialog.exec()


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
        初始化tree_view配置及数据
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
            # if addr in self.active_ip_list:
            #     qicon = self.icon_connect
            # else:
            #     qicon = self.icon_disconnect
            item = QStandardItem(addr)
            item.ip = addr
            item.type = TYPE_DEVICE
            device_item.appendRow(item)
            self.local_ip_List.append(addr)
        # 添加列
        self.tree_model.appendColumn([device_item, other_item])
        # setHeaderData 要放在appendColumn之后
        self.tree_model.setHeaderData(0, Qt.Horizontal, '设备信息')
        treeView = self.tree_view
        treeView.setContextMenuPolicy(Qt.CustomContextMenu)
        treeView.setRootIsDecorated(False)
        treeView.customContextMenuRequested.connect(self.on_ip_menu_show) # 右键菜单显示函数
        treeView.setEditTriggers(QAbstractItemView.NoEditTriggers)
        # set model to treeview
        treeView.setModel(self.tree_model)
        treeView.doubleClicked.connect(self.on_tree_item_double_clicked)
        treeView.clicked.connect(self.on_tree_item_clicked)
        # 展开整个树形视图
        treeView.expandAll()

        self.set_treeview_default_index(treeView)

        self.check_device_status()
        # 右键菜单键设置
        treeView.contextMenu = QMenu()
        # self.actionC.setDisabled(True)
        self.action_disconnect = treeView.contextMenu.addAction(self.icon_disconnect, '| 断开连接')
        self.action_disconnect.triggered.connect(self.disconnect_device)
        self.action_remove_device = treeView.contextMenu.addAction(self.icon_disconnect, '| 删除设备')
        self.action_remove_device.triggered.connect(self.del_device)

    def set_treeview_default_index(self, treeView):
        """
        设置treeview默认选中项
        :param treeView:
        :return:
        """
        root = self.tree_model.invisibleRootItem()
        if root.hasChildren():
            child = root.child(0, 0).child(0, 0)
            if child:
                idnex = self.tree_model.indexFromItem(child)
                treeView.setCurrentIndex(idnex)

    def get_current_item_model(self):
        # 得到当前选中项的 QModelIndex
        item_child_index = self.tree_view.currentIndex()
        # 获取取到QStandardItem
        item_model = self.tree_model.itemFromIndex(item_child_index)
        return item_model

    def on_ip_menu_show(self):
        item_model = self.get_current_item_model()
        if is_device_node(item_model):
            if item_model.ip in self.active_ip_list:
                self.action_remove_device.setDisabled(True)
                self.action_disconnect.setDisabled(False)
            else:
                self.action_remove_device.setDisabled(False)
                self.action_disconnect.setDisabled(True)
            self.tree_view.contextMenu.move(QCursor.pos())  # 移动到鼠标点击位置
            self.tree_view.contextMenu.show()

    @pyqtSlot()
    def del_device(self):
        """
        设备删除
        :return:
        """
        item_child_index = self.tree_view.currentIndex()
        item_model = self.tree_model.itemFromIndex(item_child_index)
        item_model.parent().removeRow(item_child_index.row())
        result, msg = self.dbManager.remove_device_from_db(item_model.ip)
        if result:
            z_logger.info('删除设备(%s)成功!' % str(item_model.ip))
            self.local_ip_List.remove(item_model.ip)

    @pyqtSlot()
    def add_device(self, ip):
        z_logger.info("设备添加成功:" + ip)
        item = QStandardItem(self.icon_disconnect, ip)
        item.ip = ip
        item.type = TYPE_DEVICE
        # FIXME 优化，此方法可能None异常
        item_model = self.get_current_item_model()
        item_model.parent().appendRow(item)
        result, msg = self.dbManager.add_device_to_db(ip)
        if result:
            z_logger.debug("已储存新设备至数据库")
            self.local_ip_List.append(ip)

    @pyqtSlot(QModelIndex)
    def on_tree_item_double_clicked(self, index):
        # 当树状item被点击时，可以通过获取item类型来处罚设备点击逻辑
        item = self.tree_model.itemFromIndex(index)  # QStandardItem
        if is_device_node(item):
            z_logger.debug("on_tree_item_double_clicked %s" % item.ip)
            self.connect_device(item.ip)
        elif item.type == 'DeviceRoot':
            # z_logger.debug('刷新设备状态')
            self.check_device_status()

    @pyqtSlot(QModelIndex)
    def on_tree_item_clicked(self, index):
        """
        When the user clicks a valid row in the tree by single clicking
        :param index: model index of the clicked row in the tree
        :return:
        """
        # self.stackedWidget_param.setCurrentIndex(index)
        item = self.tree_model.itemFromIndex(index)
        if is_device_node(item):
            z_logger.debug('on_tree_item_clicked:' + item.ip)
            self.current_device_ip = item.ip
            # TODO 更新设备信息

    def update_current_treeitem(self, isconnect: True):
        """
        更新tree的子节点
        :param isconnect: 是否为连接状态
        :return:
        """
        item_model = self.get_current_item_model()
        if is_device_node(item_model):
            if isconnect:
                item_model.setIcon(self.icon_connect)
            else:
                item_model.setIcon(self.icon_disconnect)
        else:
            z_logger.debug("无需更新树形节点")

    def refresh_treeview_by_data(self):
        """
        更新treeView现有数据的样式（目前只是连接状态样式）
        :return:
        """
        rowCount = self.tree_model.rowCount()
        z_logger.debug("refresh treeview with active_ip_list.")
        for index in range(rowCount):
            item: QStandardItem = self.tree_model.item(index)
            if item.type != "DeviceRoot":
                continue
            device_ips = item.rowCount()
            for child_index in range(device_ips):
                child = item.child(child_index)
                if child.ip in self.active_ip_list:
                    child.setIcon(self.icon_connect)
                else:
                    child.setIcon(self.icon_disconnect)

# ----------------------------------左侧TreeView End-----------------------------------------------

# ----------------------------------ADB 操作 START-----------------------------------------------
    @pyqtSlot()
    def disconnect_device(self):
        item_model = self.get_current_item_model()
        if is_device_node(item_model):
            self.temp_disconnect_ip = item_model.ip
            self.adbTools.disconnect_device(item_model.ip, self.on_adb_cmd_exectued)

    def connect_device(self, addr):
        self.adbTools.connect_device(addr, self.on_adb_cmd_exectued)

    @pyqtSlot()
    def check_device_status(self):
        z_logger.debug("check device status....")
        self.adbTools.get_devices_state(self.on_adb_cmd_exectued)

    @pyqtSlot(list)
    def on_adb_cmd_exectued(self, result):
        z_logger.debug('On adb cmd result:' + str(result))
        if "cmdExectuedTimeout" in result:
            z_logger.info("命令执行超时!")
            return

        if self.adbTools.current_cmd.startswith('adb connect'):
            if 'already connected to' in result:
                z_logger.info("Already connected!")
            else:
                # 可能会存在空的情况
                z_logger.info("设备连接成功!")
                self.check_device_status()
        elif self.adbTools.current_cmd.startswith('adb disconnect'):
            z_logger.info("设备断开成功!")
            self.active_ip_list.remove(self.temp_disconnect_ip)
            self.update_current_treeitem(False)
        elif self.adbTools.current_cmd == 'adb devices':
            self.parse_devices_states(result)

    def parse_devices_states(self, result):
        """
        解析ADb连接的设备状态数据
        :param result: List data:
            ['List of devices attached',
            '172.31.10.236:5555\tdevice']
        :return:
        """
        # 每次都清除本地记录的活跃设备数据
        self.active_ip_list.clear()

        for line in result:
            if line.startswith("List of devices attached"):
                continue
            dev_line = line.split("\t")
            if len(dev_line) < 2:
                continue
            device_name = dev_line[0]
            device_state = dev_line[1]
            if is_device_active(device_state):
                # 正常连接的设备
                z_logger.debug("active devices:" + line)
                if device_name not in self.active_ip_list:
                    self.active_ip_list.append(device_name)
                    self.dbManager.add_device_to_db(device_name)
                    self.update_current_treeitem(False)
                else:
                    z_logger.debug("Already in local.(%s)" % line)
            else:
                # 离线设备 device_state == 'offline' 或 'unknow'
                self.dbManager.change_device_state(device_name,False)
                self.update_current_treeitem(False)

        if len(self.active_ip_list) > 0 and self.current_device_ip is None:
            # FIXME 手机端设备是名称
            self.current_device_ip = self.active_ip_list[0]
            # TODO 同步数据库中的设备状态
            z_logger.info("当前选中设备：" + self.current_device_ip)

        z_logger.debug("当前已连接设备列表：" + str(self.active_ip_list))
        self.refresh_treeview_by_data()

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
