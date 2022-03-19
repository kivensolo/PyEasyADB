from PyQt5.QtCore import QSize, QVersionNumber, Qt, QT_VERSION_STR, pyqtSlot, QModelIndex
from PyQt5 import QtCore, QtWidgets
from PyQt5.QtGui import QIcon, QFont, QPixmap, QColor, QStandardItemModel, QStandardItem
from PyQt5.QtWidgets import QMessageBox, QApplication, QPushButton, QComboBox, QLabel, QMenuBar, QMenu, QAction, \
    QStatusBar, QToolTip, qApp, QTextEdit, QLineEdit, QVBoxLayout, QGroupBox, QGridLayout, QHBoxLayout, QMainWindow, \
    QToolBar, QSplitter, QTreeView, QAbstractItemView, QWidget

from config.settings import PATH_LOGO_ICON, APP_VERSION
from logcat import log
from ui import DevicePage
from ui.DeviceGroup import OldUIManager
from ui.widget.ButtomConsoleWindow import ButtomWindow
from ui.widget.NewConnectDialog import NewConnectDialog
from utils.UITools import IconTool
from utils.UiWidgts import AppPushButton
from utils.Utils import Utils

from ui.BaseWindow import BaseWindow

def initBtnTips():
    # 这种静态的方法设置一个用于显示工具提示的字体。这里使用10px滑体字体。
    QToolTip.setFont(QFont('SansSerif', 10))

class MainWindow(BaseWindow):
    """
    QMainWindow 类提供了一个主要的应用程序窗口。
    用它可以让应用程序添加状态栏,工具栏和菜单栏。
    """
    def __init__(self):
        super().__init__()
        self.initWindow()
        # 主窗口分割器
        self.main_splitter = None
        # 内容显示的分割器
        self.content_splitter = None
        # 底部控制台窗口
        self.bottom_console_window = None

        self.left_panel = None
        self.center_panel = None
        self.right_panel = None
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
        self.main_splitter = QSplitter(Qt.Vertical)
        self.main_splitter.setHandleWidth(0)
        self.main_splitter.addWidget(self.content_splitter)
        self.main_splitter.addWidget(self.bottom_console_window)
        self.main_splitter.setStretchFactor(0, 5)
        self.main_splitter.setStretchFactor(1, 4)
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
        tool_item_add_new = AppPushButton(toolbar, self.add_new_connect)
        tool_item_add_new.setStyleSheet("QPushButton:pressed{background-color:rgb(206,220,232)}")
        tool_item_add_new.setIcon(icon)
        tool_item_add_new.setStatusTip("新建连接")
        tool_item_add_new.setIconSize(QSize(24, 20))
        # tool_item_add_new.setFlat(True)  # 按钮扁平化,去掉按钮边框

        # self.tool_item_add_new.setGeometry(QtCore.QRect(0, 0, 120, 120))
        toolbar.addWidget(tool_item_add_new)
        self.addToolBar(toolbar)

    @pyqtSlot()
    def add_new_connect(self):
        print("slot_a1 ")
        new_connect = NewConnectDialog()
        new_connect.finishSignal.connect(self.onExecConnect)
        new_connect.show()
        # FIXME 执行dialog show之后，应用退出

    def onExecConnect(self, url):
        print("onExecConnect ")
        self.statusBar().showMessage(url)

    def init_center_panel(self):
        self.center_panel = QWidget()
        layout = QHBoxLayout()
        # left, top, right, bottom
        layout.setContentsMargins(5, 5, 5, 5)
        centralwidget = QWidget(self)
        centralwidget.setObjectName("centralwidget")
        centralwidget.setStyleSheet("background-color: #0000")
        # centralwidget.setGeometry(QtCore.QRect(221, 70, 500, 400))
        OldUIManager(self, centralwidget).initViews()
        layout.addWidget(centralwidget)
        self.center_panel.setLayout(layout)

    def init_left_panel(self):
        self.left_panel = QWidget()
        layout = QVBoxLayout()
        layout.setContentsMargins(0, 0, 6, 0)  # left, top, right, bottom
        tree_view = QTreeView()
        layout.addWidget(tree_view)
        self.left_panel.setLayout(layout)

        # stackedWidget_param = QtWidgets.QStackedWidget(self)  # QStackedWidget表示多分页的窗口
        # stackedWidget_param.setObjectName("stackedWidget_param")
        # stackedWidget_param.setGeometry(QtCore.QRect(1, 70, 220, 400))
        # stackedWidget_param.setStyleSheet("QWidget{background-color:rgb(188,188,188);border:none}")
        tree_model = QStandardItemModel()
        device_item = QStandardItem("Devices")
        device_item.type = 'deviceRoot'
        device_item.removeRows(0, device_item.rowCount())
        # TODO 获取设备信息
        row = QStandardItem("192.168.1.1")
        row.name = "ZTE"
        row.type = "TV"
        device_item.appendRow(row)
        tree_model.appendColumn([device_item])
        tree_model.setHeaderData(0, Qt.Horizontal, '设备信息')

        # 数据绑定至UI
        tree_view.setModel(tree_model)
        tree_view.setEditTriggers(QAbstractItemView.NoEditTriggers)
        tree_view.setContextMenuPolicy(Qt.CustomContextMenu)
        # tree_view.customContextMenuRequested.connect(self.openContextMenu)
        tree_view.doubleClicked.connect(self.onTreeItemDoubleClicked)
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

    def init_test_func_group(self):
        # 创建二级菜单栏 的多分页窗口
        stackedWidget_func = QtWidgets.QStackedWidget(self) # QStackedWidget表示多分页的窗口
        stackedWidget_func.setObjectName("stackedWidget_func")
        stackedWidget_func.setGeometry(QtCore.QRect(0, 20, 1280, 50))
        stackedWidget_func.setStyleSheet(
            "QWidget{background-color:rgb(211,240,168);border:none}"
        )

        # 2.2 创建分页对象，并载入分页
        file_page = DevicePage.DeviceA_Area()
        stackedWidget_func.addWidget(file_page)
        common_page = DevicePage.DeviceB_Area()
        stackedWidget_func.addWidget(common_page)
        advance_page = DevicePage.DeviceA_Area()
        stackedWidget_func.addWidget(advance_page)

    #     stackedWidget_func.setCurrentIndex(0) 切换至选中页

    def initWindow(self):
        # 窗口初始化
        super(MainWindow, self).initWindow()
        self.statusBar().showMessage('ready')
        self.resize(int(Utils.getWindowWidth()*0.8), int(Utils.getWindowHeight()*0.8))

    def setupUi(self):
        """ 初始化View  """
        # 基础Qt Widget

    @pyqtSlot(QModelIndex)
    def onTreeItemDoubleClicked(self, index):
        # 当树状item被点击时，可以通过获取item类型来处罚设备点击逻辑
        item = self.treeModel.itemFromIndex(index)
        print("onTreeItemDoubleClicked %s" % item.type)

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
