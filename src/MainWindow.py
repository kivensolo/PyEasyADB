import xml.dom.minidom

from PyQt5 import QtCore, QtWidgets
from PyQt5.QtCore import QVersionNumber, Qt, QT_VERSION_STR, pyqtSlot, QModelIndex, QSettings
from PyQt5.QtGui import QFont, QStandardItemModel, QStandardItem, QCursor, QBrush, QColor
from PyQt5.QtWidgets import QApplication, QMenu, QStatusBar, QToolTip, QVBoxLayout, QSplitter, QTreeView, \
    QAbstractItemView, QWidget, QStyleFactory, QMessageBox

from AppConfigManager import AppConfigManager
from src import TreeItemType
from src.BaseWindow import BaseWindow
from src.BottomWindow import BottomTabWidget
from src.CenterWindow import CommonFunctionalWidget
from src.DataBase import DBManager
from src.DevicesWatcher import DevicesWatcher
from src.MenuBar import MenuActions
from src.ToolBar import Ui_ToolBar
from src.logcat.log import z_logger
from src.settings import APP_SCREEN_RQTIO, PATH_CONFIG_OF_ADB_CMDS, COMMON_CONFIG_FILE_PATH
from src.widget.Dialogs import NewConnectDialog, AboutDialog, device_alis_edit_dialog, APKHelperDialog
from src.widget.win32Wrapper import ScrcpyEmbedWidget
from utils.ADBTools import ADBTools, ActionCmdParams
from utils.CmdExecutor import CmdExecutor
from utils.PackageManager import PackageManager
from utils.Tools import getSongFontStyle
from utils.UITools import IconTool, UiUtils
from utils.Utils import Utils


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


config_manager = AppConfigManager(COMMON_CONFIG_FILE_PATH)


class MainWindow(BaseWindow):
    treeModel = None
    tree_data_list = []

    """
    QMainWindow 类提供了一个主要的应用程序窗口。
    用它可以让应用程序添加状态栏,工具栏和菜单栏。
    """
    def __init__(self):
        super().__init__()

        # 启动设备自动检测器
        self.devicesWatcher = DevicesWatcher()
        self.devicesWatcher.changedSignal.connect(self.on_devices_refreshed)
        self.devicesWatcher.start()

        self.garbge_ip = None
        self.settings = QSettings('EasyADB_Tool', 'settings')

        self.device_menu_action_remove_device = None
        self.device_menu_action_disconnect = None
        self.device_menu_action_edit_name = None
        self.toolbar = None
        self.initWindow()

        self.icon_connect = IconTool.buildQIcon("state_connect_normal.png")
        self.icon_offline = IconTool.buildQIcon("state_connect_offline.png")
        self.icon_disconnect = IconTool.buildQIcon("state_disconnect.png")
        self.icon_warning = IconTool.buildQIcon("warning.png")
        self.icon_edit = IconTool.buildQIcon("edit.png")

        # 初始化数据库帮助类
        self.dbManager = DBManager()
        self.pkgManager = PackageManager()
        # 当前选择的设备地址信息
        self.current_device_addr = ""

        # 主窗口分割器
        self.horizontal_splitter = QSplitter(Qt.Vertical)
        # 内容显示的分割器
        self.vertical_splitter = QSplitter(Qt.Horizontal)
        # 底部控制台窗口
        self.bottom_tab_widget = BottomTabWidget(self)
        # 左侧面板相关变量
        self.left_panel = None
        self.tree_view = None

        self.center_panel = None
        self.right_panel = None

        # 连接的设备信息(包含离线设备) {"name1":"devices";"name2":"offline"}
        self.connected_device_list = {}

        self.adbTools = ADBTools()
        self.init_all_ui()
        self.show()

    def init_all_ui(self):
        # 初始化菜单栏
        MenuActions().setupUi(self)

        self.toolbar = Ui_ToolBar(self)
        self.addToolBar(self.toolbar)

        self.init_left_panel()
        self.init_center_panel()

        # 初始化底部状态栏
        self.init_status_bar()

        # 将各组件组合
        self.vertical_splitter.setHandleWidth(0)  # thing to grab the splitter
        self.vertical_splitter.addWidget(self.left_panel)
        self.vertical_splitter.addWidget(self.center_panel)
        self.vertical_splitter.setStretchFactor(1, 5)
        self.vertical_splitter.setChildrenCollapsible(0)  # 过窄不可隐藏子控件
        self.horizontal_splitter.setHandleWidth(0)
        self.horizontal_splitter.addWidget(self.vertical_splitter)
        self.horizontal_splitter.addWidget(self.bottom_tab_widget)
        self.horizontal_splitter.setStretchFactor(0, 8)
        self.horizontal_splitter.setStretchFactor(1, 2)
        self.horizontal_splitter.setChildrenCollapsible(0)  # 过窄不可隐藏子控件
        self.setCentralWidget(self.horizontal_splitter)

        initBtnTips()
        # 连接信号槽 Signals & slots. PyQt5的事件机制 http://code.py40.com/2004.html
        QtCore.QMetaObject.connectSlotsByName(self)

    def closeEvent(self, event):

        z_logger.close_log_handlers()
        event.accept()
        #  关闭窗口的时候,触发QCloseEvent。重写closeEvent()事件处理程序
        super().closeEvent(event)

        # # 释放log的handler防止文件句柄一直被持有
        # reply = QMessageBox.question(self, '提示', "确认关闭应用?",
        #                              QMessageBox.Yes | QMessageBox.No,
        #                              QMessageBox.No)
        # if reply == QMessageBox.Yes:
        #     event.accept()
        #     #  关闭窗口的时候,触发QCloseEvent。重写closeEvent()事件处理程序
        #     super().closeEvent(event)
        # else:
        #     event.ignore()

    def init_center_panel(self):
        """
        初始化中部区域的UI
        :return:
        """
        self.center_panel = QtWidgets.QTabWidget(self)
        self.center_panel.setFont(getSongFontStyle(size=10))
        self.center_panel.setObjectName("center_widget")
        # App操作页面
        appOprateArea = CommonFunctionalWidget(self)
        # 设备实时预览的UI
        embedWidget = ScrcpyEmbedWidget(self)

        self.center_panel.addTab(appOprateArea, "常用操作")
        self.center_panel.addTab(embedWidget, "设备实时预览")

    def init_left_panel(self):
        """
        初始化左侧面板
        :return: None
        """
        layout = QVBoxLayout()
        self.left_panel = QWidget()
        layout.setContentsMargins(0, 0, 6, 0)  # left, top, right, bottom

        self.init_tree_view()
        layout.addWidget(self.tree_view)
        self.left_panel.setLayout(layout)

    def init_status_bar(self):
        statusbar = QStatusBar(self)
        statusbar.setObjectName("statusbar")
        statusbar.setStyleSheet("background-color:rgb(242,242,242)")
        self.setStatusBar(statusbar)

    @pyqtSlot()
    def show_new_device_dialog(self):
        z_logger.debug("Show new connect dialog.")
        new_connect_dialog = NewConnectDialog(self, self.connect_device)
        new_connect_dialog.setWindowModality(Qt.ApplicationModal)
        new_connect_dialog.exec()

    @pyqtSlot()
    def show_about_dialog(self):
        aboutDialog = AboutDialog(self)
        aboutDialog.setWindowModality(Qt.ApplicationModal)
        aboutDialog.exec()

    @pyqtSlot()
    def show_apk_helper_dialog(self):
        """
        展示APK Helper 弹窗
        :return:
        """
        apkHelperDialog = APKHelperDialog(self)
        apkHelperDialog.setWindowModality(Qt.ApplicationModal)
        apkHelperDialog.exec()

    def initWindow(self):
        self.resize(
            int(UiUtils.getWindowWidth() * APP_SCREEN_RQTIO),
            int(UiUtils.getWindowHeight() * APP_SCREEN_RQTIO)
        )
        self.statusBar().showMessage('ready')
        super(MainWindow, self).initWindow()

    def is_current_device_connect(self):
        """
        当前选中设备是否已连接
        :return:
        """
        hasIn = self.current_device_addr in self.connected_device_list
        if not hasIn:
            z_logger.error("当前所选设备未连接，请先进行连接！")
        return hasIn

    def has_any_connected_devices(self):
        """
        是否有连接设备的设备存在
        :return:
        """
        has_connected = len(self.connected_device_list) != 0
        if not has_connected:
            z_logger.error("请先连接设备!")
        return has_connected

    # ----------------------------------左侧TreeView START-----------------------------------------------
    def init_tree_view(self):
        """
        初始化tree_view配置及数据
        :return:
        """
        # 创建tree_view
        self.tree_view = QTreeView()
        self.tree_view.setStyleSheet("""
            QTreeView::item:selected {
                background-color: #90caf9;
                color:#000000;
            }
            QTreeView::item:hover {
                background-color: #bbdefb;
            }
        """)

        self.__loadAdbCmds()
        # 获取所有本地缓存ip数据
        all_device = self.dbManager.get_all_device()

        device_item = QStandardItem("设备列表")
        device_item.setBackground(QBrush(QColor("#f0f0f0")))
        # self.tree_model.setItem(0, 1, device_item2)
        device_item.type = TreeItemType.TYPE_ROOT_DEVICE
        device_item.removeRows(0, device_item.rowCount())
        all_device.sort()
        for device in all_device:
            # 填充每一个设备信息  Format: ip:port(alias)
            if device[1] == "0":
                addr = device[0]
            else:
                addr = device[0] + ":" + device[1]
            alias = device[2]
            show_name = addr
            if alias:
                show_name = f"{addr}({alias})"
            item = QStandardItem(show_name)
            item.addr = addr
            item.alias = alias
            item.desc = device[3]
            item.type = TreeItemType.TYPE_DEVICE
            device_item.appendRow(item)

        # 设备数据插入到第一条中
        self.tree_data_list.insert(0, device_item)

        # TreeModel
        self.treeModel = QStandardItemModel()
        self.treeModel.appendColumn(self.tree_data_list)
        # setHeaderData 要放在appendColumn之后
        self.treeModel.setHeaderData(0, Qt.Horizontal, '功能区')

        # TreeView设置
        self.tree_view.setContextMenuPolicy(Qt.CustomContextMenu)
        self.tree_view.setRootIsDecorated(False)
        self.tree_view.customContextMenuRequested.connect(self.on_ip_menu_show)  # 右键菜单显示函数
        self.tree_view.setEditTriggers(QAbstractItemView.NoEditTriggers)
        # set model to treeview
        self.tree_view.setModel(self.treeModel)
        self.tree_view.doubleClicked.connect(self.on_tree_item_double_clicked)
        self.tree_view.clicked.connect(self.on_tree_item_clicked)
        # 设置成有虚线连接的方式
        self.tree_view.setStyle(QStyleFactory.create('windows'))
        # 展开整个树形视图
        self.tree_view.expandAll()

        # 右键菜单键设置
        self.tree_view.contextMenu = QMenu()
        # self.actionC.setDisabled(True)
        self.device_menu_action_edit_name = self.tree_view.contextMenu.addAction(self.icon_edit, '| 备注设置')
        self.device_menu_action_edit_name.triggered.connect(self.show_device_alias_edit_dialog)
        self.device_menu_action_disconnect = self.tree_view.contextMenu.addAction(self.icon_disconnect, '| 断开连接')
        self.device_menu_action_disconnect.triggered.connect(self.disconnect_device)
        self.device_menu_action_remove_device = self.tree_view.contextMenu.addAction(self.icon_warning, '| 删除设备')
        self.device_menu_action_remove_device.triggered.connect(self.del_device)

    def __loadAdbCmds(self):
        root_adb_node = QStandardItem("命令列表")
        root_adb_node.setBackground(QBrush(QColor("#f0f0f0")))
        dom = xml.dom.minidom.parse(PATH_CONFIG_OF_ADB_CMDS)
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
            print("\t|====分组：" + _attrName)
            current_item = QStandardItem(_attrName)
            if prentQItem is not None:
                prentQItem.appendRow(current_item)

            # load child group element
            _groupChildrens = group.getElementsByTagName("sub_group")
            if _groupChildrens.length > 0:  # 子分组
                self.parseNode(current_item, _groupChildrens, level+1)

            _childrens = group.childNodes
            for childNode in _childrens:
                if childNode.nodeName == 'item':
                    # 获取item节点的name和文本内容
                    name = childNode.getAttribute("name")
                    cmd = None
                    if childNode.firstChild is not None:
                        cmd = childNode.firstChild.data
                    print("\t\t|" + name + ":" + str(cmd))
                    qItem = QStandardItem(name)
                    qItem.type = TreeItemType.TYPE_ADB_CMD
                    qItem.name = name
                    # 默认不需要指定应用包名
                    qItem.needDstPkg = childNode.getAttribute("dst_pkg") == "true"
                    # 默认为shell模式
                    qItem.isShell = childNode.getAttribute("shell") != "false"
                    qItem.cmd = cmd
                    current_item.appendRow(qItem)
            if level == 1:
                self.tree_data_list.append(prentQItem)

    def set_treeview_default_index(self):
        """
        设置treeview默认选中项
        优先选择第一个已连接的设备，如果没有已连接，则默认选中第一个设备
        :return:
        """
        z_logger.debug("Find defalut selected device ip....")
        root = self.treeModel.invisibleRootItem()
        if root.hasChildren():
            child = root.child(0, 0).child(0, 0)
            if child:
                index = self.treeModel.indexFromItem(child)
                self.tree_view.setCurrentIndex(index)
                self.on_tree_item_clicked(index)

    def get_current_standard_item(self):
        # 得到当前选中项的 QModelIndex
        item_child_index = self.tree_view.currentIndex()
        # 获取取到QStandardItem
        item_model = self.treeModel.itemFromIndex(item_child_index)
        return item_model

    def on_ip_menu_show(self):
        item_model = self.get_current_standard_item()
        if is_device_node(item_model):
            if item_model.addr in self.connected_device_list:
                self.device_menu_action_remove_device.setDisabled(True)
                self.device_menu_action_disconnect.setDisabled(False)
            else:
                self.device_menu_action_remove_device.setDisabled(False)
                self.device_menu_action_disconnect.setDisabled(True)
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
            self.devicesWatcher.onDeviceDeleted()

    @pyqtSlot()
    def show_device_alias_edit_dialog(self):
        """
        弹出编辑设备别名的弹窗
        :return:
        """
        _device_alis_dialog = device_alis_edit_dialog(self)
        _device_alis_dialog.on_alias_update_signal.connect(self.on_device_alias_update)
        _device_alis_dialog.setWindowModality(Qt.ApplicationModal)
        _device_alis_dialog.exec()

    @pyqtSlot(str)
    def on_device_alias_update(self, result):
        current_index = self.tree_view.currentIndex()
        item: QStandardItem = self.treeModel.itemFromIndex(current_index)
        item.alias = result
        item.setText(f"{item.addr}({result})")

    @pyqtSlot()
    def onNewDeviceAdded(self, _addr):
        """
        设备成功加入时的回调函数
        成功加入的设备，可以是手动输入的已连接|未连接设备;
        也可以是自动检测到的已连接但没加入进来的设备。
        :param _addr: 设备标识信息
        常规设备是[ip:port]的格式
        模拟器或者真机可能是字符串名称，比如: emulator-5554
        还有可能设备的名称为纯数字，比如小米音响, 名称为: 0184059035100000170
        :return:
        """
        z_logger.info_with_stamp("已添加新设备:" + _addr)
        item = QStandardItem(self.icon_disconnect, _addr)
        item.addr = _addr
        item.type = TreeItemType.TYPE_DEVICE
        item_model = self.get_current_standard_item()
        if is_device_node(item_model):
            item_model.parent().appendRow(item)
        elif is_device_root_node(item_model):
            item_model.appendRow(item)
        else:
            # 首次启动添加新设备
            device_group_item: QStandardItem = self.tree_data_list[0]
            deviceItem: QStandardItem = QStandardItem(_addr)
            deviceItem.addr = _addr
            deviceItem.type = TreeItemType.TYPE_DEVICE
            device_group_item.appendRow(deviceItem)
            self.set_treeview_default_index()

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
            if item.addr not in self.connected_device_list:
                z_logger.info(f"连接{item.addr}......")
                self.connect_device(item.addr)
            else:
                z_logger.debug("Already in active device list.")
        elif item.type == TreeItemType.TYPE_ADB_CMD:
            params: ActionCmdParams = ActionCmdParams()
            params.isShellMode = item.isShell
            params.needDstPkg = item.needDstPkg
            params.cmd = item.cmd
            self.runAdbCMD(params)

    def runAdbCMD(self, cmdParams: ActionCmdParams):
        """
        执行单次的ADB命令
        :param cmdParams:   命令封装对象
        :return:
        """
        if self.has_any_connected_devices():
            # Step_1:Complete target app parameter
            if cmdParams.needDstPkg:
                cmdParams.target_app = self.pkgManager.getSelectedPackageName()

            # Step_2:Check if this action needs to rely on package name.
            _checkPass = cmdParams.verifyTargetApp()
            if not _checkPass:
                return

            # Step_3: Custom confirm tips
            if "uninstall" in cmdParams.cmd:
                reply = QMessageBox.question(
                    self, '提示', f"确认卸载以下应用:\n {cmdParams.target_app}",
                    QMessageBox.Yes | QMessageBox.No,
                    QMessageBox.No)
                if reply != QMessageBox.Yes:
                    return

            # Step_4: Update target device
            cmdParams.target_device_ip = self.current_device_addr

            self.adbTools.exec_adb_cmd(cmdParams.getAdbCMD(), block=self.on_adb_cmd_exectued)

    def runAdbCMD_V2(self, cmdParams: list):
        if self.has_any_connected_devices():
            for _cmd in cmdParams:
                pkgName = ""
                if _cmd.needDstPkg:
                    pkgName = self.pkgManager.getSelectedPackageName()
                    if pkgName is None:
                        z_logger.error("请先选择目标应用")
                        return
                # 每次重新赋值
                _cmd.target_device_ip = self.current_device_addr
                _cmd.target_app = pkgName
                self.adbTools.exec_adb_cmd(_cmd.getAdbCMD(), block=self.on_adb_cmd_exectued)

    @pyqtSlot(QModelIndex)
    def on_tree_item_clicked(self, index):
        """
        When the user clicks a valid row in the tree by single clicking
        :param index: model index of the clicked row in the tree
        :return:
        """
        item = self.treeModel.itemFromIndex(index)
        if is_device_node(item):
            isconencted = item.addr in self.connected_device_list
            z_logger.debug(f'当前选中设备:{item.addr}  是否已连接:{isconencted}')
            if self.current_device_addr == item.addr and isconencted:
                return
            self.current_device_addr = item.addr
            self.bottom_tab_widget.updateSelectDeviceInfo(self.current_device_addr, isconencted)

    def update_current_tree_item(self, isconnect: True):
        """
        更新tree的子节点
        :param isconnect: 是否为连接状态
        :return:
        """
        item_model = self.get_current_standard_item()
        if is_device_node(item_model):
            if isconnect:
                item_model.setIcon(self.icon_connect)
            else:
                item_model.setIcon(self.icon_disconnect)
            # item_model.setIconSize(QSize(128, 128))
        elif is_device_root_node(item_model):
            z_logger.debug("更新设备列表")
        else:
            z_logger.debug("无需更新节点")

    def refresh_treeview_by_data(self):
        """
        更新treeView现有数据的样式
        若为第一次刷新数据(has_device_selected为false),则尝试模式选中第一个已连接设备。
        若没有已连接设备，则默认选择第一个设备。
        :return:
        """
        has_device_selected = len(self.current_device_addr) != 0
        rowCount = self.treeModel.rowCount()
        z_logger.debug("Refresh treeview with active_ip_list.")
        for row_index in range(rowCount):
            item: QStandardItem = self.treeModel.item(row_index)
            if not is_device_root_node(item):
                continue
            device_ips = item.rowCount()
            for child_index in range(device_ips):
                child = item.child(child_index)
                if child.addr in self.connected_device_list:
                    _state = self.connected_device_list[child.addr]
                    if _state == "device":
                        child.setIcon(self.icon_connect)
                    else:
                        child.setIcon(self.icon_offline)
                    # 没有选中设备时，选择第一个已连接设备
                    if not has_device_selected:
                        has_device_selected = True
                        model_index: QModelIndex = self.treeModel.indexFromItem(child)
                        self.tree_view.setCurrentIndex(model_index)
                        self.on_tree_item_clicked(model_index)
                else:
                    child.setIcon(self.icon_disconnect)

            if not has_device_selected:
                # 若没有一个设备已连接，则默认选中第一个设备
                self.set_treeview_default_index()


# ----------------------------------左侧TreeView End-----------------------------------------------

# ----------------------------------ADB 操作 START-----------------------------------------------
    @pyqtSlot()
    def disconnect_device(self):
        item_model = self.get_current_standard_item()
        if is_device_node(item_model):
            self.garbge_ip = item_model.addr
            if item_model.addr == self.current_device_addr:
                z_logger.debug("断开设备为当前选中的连接设备,检查live log.")
                # 断开当前设备时,检查live log
                self.bottom_tab_widget.liveLogView.destoryLiveLog()
            self.adbTools.disconnect_device(item_model.addr, self.on_adb_cmd_exectued)

    @pyqtSlot()
    def connect_device(self, addr):
        z_logger.debug("Start to connect " + addr)
        self.adbTools.connect_device(addr, self.on_adb_cmd_exectued)

    @pyqtSlot()
    def check_device_status(self):
        z_logger.debug("Check device status....")
        # self.adbTools.get_devices_state(self.on_adb_cmd_exectued)

    @pyqtSlot(str)
    def on_adb_cmd_exectued(self, result):
        """
        ADB命令执行完毕的回调函数，进行各种命令结果的处理
        :param result: List for result.
        :return:
        """
        resultList = result.split('\n')
        z_logger.debug('On adb cmd result:' + str(result))

        _execed_cmd = self.adbTools.current_cmd

        # 结果数据特殊处理
        if "cmdExectuedTimeout" in resultList:
            if _execed_cmd.startswith('adb connect'):
                z_logger.error('设备连接超时！请确认设备是否满足连接条件！')
            else:
                z_logger.error("命令执行超时!")
            return

        if _execed_cmd.startswith('adb connect'):
            result_info = resultList[0]
            if 'already connected to' in result_info:
                # ['already connected to xxxxxx']
                z_logger.info_with_stamp("Already connected!")
            elif 'cannot connect to' in result_info:
                # ['cannot connect to xxxx: 由于连接方在一段时间后没有正确答复或连接的主机没有反应，连接尝试失败。 (10060)']
                z_logger.error(result_info)
            elif 'connected to' in result_info:
                z_logger.info("设备连接成功!!")
            else:
                # 可能会存在空的情况
                z_logger.error(f"连接时出现未知异常:[{result_info}]")
        elif _execed_cmd.startswith('adb disconnect'):
            z_logger.info("设备断开成功!")
            self.connected_device_list.pop(self.garbge_ip, "Default IP")
            self.update_current_tree_item(False)
            self.bottom_tab_widget.clearRunningProcessComBox()
        else:
            if len(result) != 0:
                z_logger.info(result)

    def on_devices_refreshed(self, watchedDevices: list):
        """
        设备连接状态变更时, 刷新设备信息
        :param watchedDevices:  包含DeviceInfo的列表
        :return:
        """
        z_logger.debug("[Refresh Devices] on_devices_refreshed()")
        z_logger.debug(f"Current select device:{self.current_device_addr}")
        updateSelectedDeviceProcessInfo = False
        self.connected_device_list.clear()

        for deviceInfo in watchedDevices:
            device_name = deviceInfo.name
            device_state = deviceInfo.state

            if deviceInfo.isConnected():
                z_logger.debug("[Refresh Devices] active devices:" + device_name)

                # 本地已连接列表中，没有此设备的话，同步数据至内存和数据库;
                _isStateOK, msg = deviceInfo.is_device_state_normal()
                z_logger.debug(f"检测到已连接设备:{device_name}[{msg}]")

                self.connected_device_list[device_name] = device_state
                exist, msg = self.dbManager.get_device_prop_info(device_name)
                if not exist:
                    z_logger.debug(f"[Refresh Devices] Find new device {device_name}, save to database.")
                    _port = "0"
                    if "." not in device_name:
                        # 不包含ip格式的点,则说明是设备名称，可能纯数字，也可能是字符和数据混杂
                        z_logger.debug("[Refresh Devices] 设备标识为名称，非ip格式, 不做处理")
                    elif ":" not in device_name:
                        _port = "5555"

                    state, name = self.dbManager.add_device_to_db(ip=device_name, port=_port)
                    if state:
                        self.onNewDeviceAdded(name)
                        # self.close()
                else:
                    z_logger.debug("[Refresh Devices] This device already in local.")

                    if _isStateOK and (self.current_device_addr == device_name):
                        updateSelectedDeviceProcessInfo = True
                    else:
                        z_logger.debug("[Parse States] But device state not normal, don't refresh processInfo.")

            else:
                # 未知状态的设备，均认为未连接
                self.dbManager.change_device_state(device_name, False)

        z_logger.debug(f"[Parse States] 当前已连接设备列表数量:{len(self.connected_device_list)} \n"
                       f" 设备列表:{str(self.connected_device_list)}")
        self.refresh_treeview_by_data()

        z_logger.debug("[Parse States] Current select device is connected.Update process info.")
        if updateSelectedDeviceProcessInfo:
            # FIXME 走子线程，不然卡状态刷新, 先临时放在refresh_treeview_by_data后执行
            self.bottom_tab_widget.updateRunningProcessInfo()

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
