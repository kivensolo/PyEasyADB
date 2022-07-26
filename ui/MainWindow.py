import xml.dom.minidom

from PyQt5.QtCore import QSize, QVersionNumber, Qt, QT_VERSION_STR, pyqtSlot, QModelIndex, QTimer, pyqtSignal
from PyQt5 import QtCore, QtWidgets
from PyQt5.QtGui import QIcon, QFont, QPixmap, QColor, QStandardItemModel, QStandardItem, QCursor
from PyQt5.QtWidgets import QMessageBox, QApplication, QPushButton, QComboBox, QLabel, QMenuBar, QMenu, QAction, \
    QStatusBar, QToolTip, qApp, QTextEdit, QLineEdit, QVBoxLayout, QGroupBox, QGridLayout, QHBoxLayout, QMainWindow, \
    QToolBar, QSplitter, QTreeView, QAbstractItemView, QWidget, QTreeWidgetItem, QListWidgetItem, QStyleFactory

from logcat.log import z_logger
from ui import TreeItemType
from ui.CenterLayout import CenterLayout
from ui.component.ToolBarView import AppToolBar
from ui.sql import DBManager
from ui.style import StyleSheetConfig
from ui.widget.ButtomConsoleWindow import ButtomWindow
from utils.ADBTools import ADBTools
from utils.CmdExecutor import CmdExecutor
from utils.UITools import IconTool
from utils.UiWidgts import AppPushButton
from utils.Utils import Utils

from ui.BaseWindow import BaseWindow


def initBtnTips():
    # 这种静态的方法设置一个用于显示工具提示的字体。这里使用10px滑体字体。
    QToolTip.setFont(QFont('SansSerif', 10))


# 未使用
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
    return item and item.type == TreeItemType.TYPE_DEVICE


def is_device_root_node(item: QStandardItem):
    """
    判断节点数据是否是设备根节点
    :param item: 节点标准数据
    :return:
    """
    return item and item.type == TreeItemType.TYPE_ROOT_DEVICE


def is_device_active(state):
    """
    设备连接状态是否正常
    :param state:
    :return:
    """
    return state == 'device'


class MainWindow(BaseWindow):
    treeModel = None
    # 本地缓存ip数据
    local_ip_List = []
    tree_data_list = []

    """
    QMainWindow 类提供了一个主要的应用程序窗口。
    用它可以让应用程序添加状态栏,工具栏和菜单栏。
    """
    def __init__(self):
        super().__init__()
        self.toolbar = None
        self.initWindow()

        self.icon_connect = IconTool.buildQIcon("state_connect.png")
        self.icon_disconnect = IconTool.buildQIcon("state_disconnect.png")

        # 初始化数据库帮助类
        self.dbManager = DBManager()
        self.current_device_addr = ""

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
        self.toolbar = AppToolBar(self)
        self.addToolBar(self.toolbar)
        # 初始化功能区
        # self.init_test_func_group()

        self.init_left_panel()
        self.init_center_panel()
        self.set_treeview_default_index(self.tree_view)

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
        # reply = QMessageBox.question(self, '提示', "要离开了么?",
        #                              QMessageBox.Yes | QMessageBox.No,
        #                              QMessageBox.No)
        # if reply == QMessageBox.Yes:
        event.accept()
        # else:
        #     event.ignore()

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
        self.centerArea = CenterLayout(self)
        self.stacked_device_info.addWidget(self.centerArea)
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

    def is_current_device_connect(self):
        return self.current_device_addr in self.active_ip_list

    # ----------------------------------左侧TreeView START-----------------------------------------------
    def init_tree_view(self):
        """
        初始化tree_view配置及数据
        :return:
        TODO 改为子线程？
        TODO 优化，学习TreeView 只需要刷新数据，而不需要重新构建UI
        https://blog.csdn.net/qq_27061049/article/details/89641210
        https://blog.csdn.net/seniorwizard/article/details/110199352?spm=1001.2101.3001.6650.8&utm_medium=distribute.pc_relevant.none-task-blog-2%7Edefault%7EBlogCommendFromBaidu%7ERate-8.pc_relevant_default&depth_1-utm_source=distribute.pc_relevant.none-task-blog-2%7Edefault%7EBlogCommendFromBaidu%7ERate-8.pc_relevant_default&utm_relevant_index=13
        """
        self.load_adb_cmds()

        self.local_ip_List.clear()
        # 获取所有本地缓存ip数据
        all_device = self.dbManager.get_all_device()

        device_item = QStandardItem("设备列表")
        # self.tree_model.setItem(0, 1, device_item2)
        device_item.type = TreeItemType.TYPE_ROOT_DEVICE
        device_item.removeRows(0, device_item.rowCount())
        # TODO 自定义排序规则
        all_device.sort()
        for device in all_device:
            addr = device[0] + ":" + device[1]  # ip:port
            # if addr in self.active_ip_list:
            #     qicon = self.icon_connect
            # else:
            #     qicon = self.icon_disconnect
            item = QStandardItem(addr)
            item.addr = addr
            item.type = TreeItemType.TYPE_DEVICE
            device_item.appendRow(item)
            self.local_ip_List.append(addr)
        # 设备数据插入到第一条中
        self.tree_data_list.insert(0, device_item)

        # TreeModel
        self.treeModel = QStandardItemModel()
        self.treeModel.appendColumn(self.tree_data_list)
        # setHeaderData 要放在appendColumn之后
        self.treeModel.setHeaderData(0, Qt.Horizontal, '功能区')

        # TreeView设置
        treeView = self.tree_view
        treeView.setContextMenuPolicy(Qt.CustomContextMenu)
        treeView.setRootIsDecorated(False)
        treeView.customContextMenuRequested.connect(self.on_ip_menu_show)  # 右键菜单显示函数
        treeView.setEditTriggers(QAbstractItemView.NoEditTriggers)
        # set model to treeview
        treeView.setModel(self.treeModel)
        treeView.doubleClicked.connect(self.on_tree_item_double_clicked)
        treeView.clicked.connect(self.on_tree_item_clicked)
        # 设置成有虚线连接的方式
        treeView.setStyle(QStyleFactory.create('windows'))
        # 展开整个树形视图
        treeView.expandAll()

        self.check_device_status()
        # 右键菜单键设置
        treeView.contextMenu = QMenu()
        # self.actionC.setDisabled(True)
        self.action_disconnect = treeView.contextMenu.addAction(self.icon_disconnect, '| 断开连接')
        self.action_disconnect.triggered.connect(self.disconnect_device)
        self.action_remove_device = treeView.contextMenu.addAction(self.icon_disconnect, '| 删除设备')
        self.action_remove_device.triggered.connect(self.del_device)

    def load_adb_cmds(self):
        root_adb_node = QStandardItem("命令列表")
        dom = xml.dom.minidom.parse("./config/cmdConfig.xml")
        root = dom.documentElement
        childNodes = root.getElementsByTagName("group")
        print("****所有分组信息****")
        self.parseNode(root_adb_node, childNodes, 1)

    def parseNode(self, prentQItem, groupElements, level):
        """
        :param prentQItem: 父级UI节点
        :param groupElements: 当前分组元素的列表
        :param level: 父级UI节点所在层级
        :return:
        """
        for group in groupElements:
            # ELEMENT_NODE
            _attrName = group.getAttribute("name")
            print("\t\t|发现分组：" + _attrName)
            currentFolder = QStandardItem(_attrName)
            if level == 1 or prentQItem is not None:
                prentQItem.appendRow(currentFolder)

            # load child group element
            _groupChildrens = group.getElementsByTagName("sub_group")
            if _groupChildrens.length > 0:
                self.parseNode(currentFolder, _groupChildrens, level+1)
            if level == 1:
                cmdChildrens = group.getElementsByTagName("item")
            else:
                cmdChildrens = group.getElementsByTagName("citem")
            for item in cmdChildrens:
                name = item.getAttribute("name")
                cmd = None
                if item.firstChild is not None:
                    cmd = item.firstChild.data
                print("\t\t|Add cmd:" + name + "=" + str(cmd))
                qItem = QStandardItem(name)
                qItem.type = TreeItemType.TYPE_ADB_CMD
                qItem.name = name
                qItem.needTarget = item.getAttribute("target")
                qItem.isShell = item.getAttribute("isShell")
                qItem.cmd = cmd
                currentFolder.appendRow(qItem)
            if level == 1:
                self.tree_data_list.append(prentQItem)

    def set_treeview_default_index(self, treeView):
        """
        设置treeview默认选中项
        :param treeView:
        :return:
        """
        root = self.treeModel.invisibleRootItem()
        if root.hasChildren():
            child = root.child(0, 0).child(0, 0)
            if child:
                index = self.treeModel.indexFromItem(child)
                treeView.setCurrentIndex(index)
                self.on_tree_item_clicked(index)

    def get_current_item_model(self):
        # 得到当前选中项的 QModelIndex
        item_child_index = self.tree_view.currentIndex()
        # 获取取到QStandardItem
        item_model = self.treeModel.itemFromIndex(item_child_index)
        return item_model

    def on_ip_menu_show(self):
        item_model = self.get_current_item_model()
        if is_device_node(item_model):
            if item_model.addr in self.active_ip_list:
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
        item_model = self.treeModel.itemFromIndex(item_child_index)
        item_model.parent().removeRow(item_child_index.row())
        result, msg = self.dbManager.remove_device_from_db(item_model.addr)
        if result:
            z_logger.info('删除设备(%s)成功!' % str(item_model.addr))
            self.local_ip_List.remove(item_model.addr)

    @pyqtSlot()
    def add_device(self, ip):
        z_logger.info("设备添加成功:" + ip)
        item = QStandardItem(self.icon_disconnect, ip)
        item.addr = ip
        item.type = TreeItemType.TYPE_DEVICE
        # FIXME 优化，此方法可能None异常
        item_model = self.get_current_item_model()
        if is_device_node(item_model):
            item_model.parent().appendRow(item)
        elif is_device_root_node(item_model):
            item_model.appendRow(item)
        else:
            z_logger.error('添加设备时，数据获取异常')
        result, msg = self.dbManager.add_device_to_db(ip)
        if result:
            z_logger.debug("已储存新设备至数据库")
            self.local_ip_List.append(ip)

    @pyqtSlot(QModelIndex)
    def on_tree_item_double_clicked(self, index):
        """
        树状Item被双击的槽函数回调
        :param index: 可以通过获取item类型来触发设备点击逻辑
        :return:
        """
        item = self.treeModel.itemFromIndex(index)  # QStandardItem
        if is_device_node(item):
            z_logger.debug("On tree item double clicked %s" % item.addr)
            if item.addr not in self.active_ip_list:
                z_logger.info("连接设备中......(%s)" % item.addr)
                self.connect_device(item.addr)
            else:
                z_logger.debug("Already in active device list.")
        elif is_device_root_node(item):
            # z_logger.debug('刷新设备状态')
            self.check_device_status()
        elif item.type == TreeItemType.TYPE_ADB_CMD:
            self._dealWithADB(item)

    def _dealWithADB(self, item):
        """
        处理ADB命令
        :param item:
        :return:
        """
        if len(self.active_ip_list) == 0:
            z_logger.error("请先连接设备")
        else:
            pkgName = ""
            if item.needTarget == "true":
                pkgName = self.toolbar.get_current_choose_pkg()
                if pkgName is None:
                    z_logger.error("请先选择目标应用")
                    return
            self.adbTools.exec_cmd(
                self.current_device_addr,
                item.cmd,
                pkgName,
                item.isShell,
                self.on_adb_cmd_exectued
            )

    @pyqtSlot(QModelIndex)
    def on_tree_item_clicked(self, index):
        """
        When the user clicks a valid row in the tree by single clicking
        :param index: model index of the clicked row in the tree
        :return:
        """
        # self.stackedWidget_param.setCurrentIndex(index)
        item = self.treeModel.itemFromIndex(index)
        if is_device_node(item):
            z_logger.debug('on_tree_item_clicked:' + item.addr)
            if self.current_device_addr == item.addr:
                return
            self.current_device_addr = item.addr
            isconencted = item.addr in self.active_ip_list
            self.toolbar.update_device_info(self.current_device_addr, isconencted)

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
        elif is_device_root_node(item_model):
            z_logger.debug("更新设备列表")
        else:
            z_logger.debug("无需更新节点")

    def refresh_treeview_by_data(self):
        """
        更新treeView现有数据的样式（目前只是连接状态样式）
        :return:
        """
        rowCount = self.treeModel.rowCount()
        z_logger.debug("Refresh treeview with active_ip_list.")
        for index in range(rowCount):
            item: QStandardItem = self.treeModel.item(index)
            if not is_device_root_node(item):
                continue
            device_ips = item.rowCount()
            for child_index in range(device_ips):
                child = item.child(child_index)
                if child.addr in self.active_ip_list:
                    child.setIcon(self.icon_connect)
                else:
                    child.setIcon(self.icon_disconnect)

# ----------------------------------左侧TreeView End-----------------------------------------------

# ----------------------------------ADB 操作 START-----------------------------------------------
    @pyqtSlot()
    def disconnect_device(self):
        item_model = self.get_current_item_model()
        if is_device_node(item_model):
            self.temp_disconnect_ip = item_model.addr
            self.adbTools.disconnect_device(item_model.addr, self.on_adb_cmd_exectued)

    def connect_device(self, addr):
        self.adbTools.connect_device(addr, self.on_adb_cmd_exectued)

    @pyqtSlot()
    def check_device_status(self):
        z_logger.debug("Check device status....")
        self.adbTools.get_devices_state(self.on_adb_cmd_exectued)

    @pyqtSlot(str)
    def on_adb_cmd_exectued(self, result):
        """
        ADB命令执行完毕的回调函数，进行各种命令结果的处理
        :param result: List for result.
        :return:
        """
        resultList = result.split('\n')
        z_logger.debug('On adb cmd result:' + str(result))
        if "cmdExectuedTimeout" in resultList:
            if self.adbTools.current_cmd.startswith('adb connect'):
                z_logger.error('很遗憾, 设备连接超时！')
            else:
                z_logger.error("命令执行超时!")
            return

        if self.adbTools.current_cmd.startswith('adb connect'):
            result_info = resultList[0]
            if 'already connected to' in result_info:
                # ['already connected to xxxxxx']
                z_logger.info("Already connected!")
            elif 'cannot connect to' in result_info:
                # ['cannot connect to xxxx: 由于连接方在一段时间后没有正确答复或连接的主机没有反应，连接尝试失败。 (10060)']
                z_logger.error(result_info)
            else:
                # 可能会存在空的情况
                z_logger.info("设备连接成功!")
                self.check_device_status()
        elif self.adbTools.current_cmd.startswith('adb disconnect'):
            z_logger.info("设备断开成功!")
            self.active_ip_list.remove(self.temp_disconnect_ip)
            self.update_current_treeitem(False)
        elif self.adbTools.current_cmd == 'adb devices':
            self.parse_devices_states(resultList)
        else:
            if len(result) != 0:
                z_logger.info("Result=" + result)

    def parse_devices_states(self, result):
        """
        解析ADb连接的设备状态数据
        :param result: List data:
            ['List of devices attached',
            '172.31.10.236:5555\tdevice']

            device , 设备连接正常
            offline , 设备离线，连接出现异常
            unauthorized 设备为进行授权，需要在设备上是否允许调试对话框进行授权
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
                z_logger.debug("[Parse States] active devices:" + line)
                if device_name not in self.active_ip_list:
                    self.active_ip_list.append(device_name)
                    # 若发现新设备,自动添加设备
                    if device_name not in self.local_ip_List:
                        z_logger.debug("[Parse States] This Device is not in local, add new：%s}" % device_name)
                        self.add_device(device_name)
                else:
                    z_logger.debug("[Parse States] Already in local.(%s)" % line)
            else:
                # 离线设备 device_state == 'offline' 或 'unknow'
                self.dbManager.change_device_state(device_name,False)
                self.update_current_treeitem(False)

        if len(self.active_ip_list) > 0 and self.current_device_addr is None:
            # FIXME 手机端设备是名称
            self.current_device_addr = self.active_ip_list[0]
            # TODO 同步数据库中的设备状态
            z_logger.info("当前选中设备：" + self.current_device_addr)

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
