from PyQt5 import QtCore
from PyQt5.QtCore import Qt
from PyQt5.QtGui import QFont, QPixmap, QColor
from PyQt5.QtWidgets import QPushButton, QLineEdit, QLabel, QComboBox, QTextEdit, QGridLayout, \
    QHBoxLayout, QGroupBox, QVBoxLayout

from config.settings import DB_NAME
from logcat import log
from ui.sql import DBManager
from utils import Tools
from utils.Tools import getKTFontStyle, getWRYHFontStyle


class OldUIManager:
    resImages = {'Linked': './res/img/link_32x32.png',
                 'Unlink': './res/img/unlink_32x28.png',
                 'Device': './res/img/device.png'}
    isDeviceConnected = False
    windwos = None

    def __init__(self, windows, parent):
        """
            @:param self: DeviceBody的实例对象本身
            @:param windows : mwindow对象
            @:param parent : 父widget
        """
        self.windows = windows

        # self.switchBtn = SwitchBtn(windows)
        # self.switchBtn.setGeometry(10, 10, 60, 30)
        # self.switchBtn.checkedChanged.connect(self.getState)

        self.deviceImageView = QLabel(parent)
        self.deviceImageView.setPixmap(QPixmap(OldUIManager.resImages['Device']))

        # ip选择View
        self.ipComboBox = QComboBox(parent)

        # 连接状态view
        self.linkImageStateView = QLabel(parent)
        pixMap = QPixmap(OldUIManager.resImages['Unlink'])
        self.linkImageStateView.setPixmap(pixMap)

        # 包名控件初始化
        self.pkg_Tip = QLabel(parent)
        self.pkg_Tip.setText('目标App包名:')
        self.pkgComboBox = QComboBox(parent)

        self.btnAddNewPkg = QPushButton(parent)
        self.btnAddNewPkg.setText("添加新包名:")
        # self.btnAddNewPkg.setGeometry(QtCore.QRect(10, 100, 101, 31))
        self.btnAddNewPkg.setObjectName("btn_addNewPkg")
        self.btnAddNewPkg.setFont(getKTFontStyle())
        self.btnAddNewPkg.clicked.connect(lambda: self.onPkgAdd())
        self.pkg_inputEditText = QLineEdit(parent)
        self.activityClassPath = QLineEdit(parent)

        # 控制台view初始化
        # self.consoleView = QTextEdit(parent)

        # self.sqlHelper = DBManager(DB_NAME)

        self.selected_ip = ''
        self.selected_pkg = ''

        self.action_start = QPushButton(parent)
        self.action_start.setText("Start")
        self.action_clear = QPushButton(parent)
        self.action_clear.setText("Clear")
        self.action_uninstall = QPushButton(parent)
        self.action_uninstall.setText("Uninstall")
        self.action_stop = QPushButton(parent)
        self.action_stop.setText("Stop")

        # 创建layout及GroupBox
        # self.gridGroupBox = QGroupBox("Grid layout")
        self.gridlayout = QGridLayout()
        self.actionLayout = QHBoxLayout()
        # self.consoleViewBox = QGroupBox("Terminal:")
        self.createGridGroupView()
        self.createActionsGroupView()
        self.createConsoleView()

        device_layout = QVBoxLayout()
        # device_layout.addStretch()
        device_layout.addLayout(self.gridlayout)
        device_layout.addLayout(self.actionLayout)
        # device_layout.addWidget(self.consoleViewBox)
        parent.setLayout(device_layout)

    def getState(self, checked):
        print("checked=", checked)

    def createConsoleView(self):
        # layout = QHBoxLayout()
        # # layout.addWidget(self.consoleView)
        # self.consoleViewBox.setLayout(layout)
        pass

    def createGridGroupView(self):
        """
        TODO 换布局  不适用GridGroupView
        创建主要功能区域的布局
        :return:
        """
        self.gridlayout.setSpacing(15)
        # 第1行
        self.gridlayout.addWidget(self.deviceImageView, 1, 0)
        self.gridlayout.addWidget(self.ipComboBox, 1, 1)
        self.gridlayout.addWidget(self.linkImageStateView, 1, 2)
        # self.gridlayout.addWidget(self.switchBtn, 1, 6)

        # 第2行
        self.gridlayout.addWidget(self.pkg_Tip, 2, 0)
        self.gridlayout.addWidget(self.pkgComboBox, 2, 1)

        # 第3行
        self.gridlayout.addWidget(self.btnAddNewPkg, 3, 0)
        self.gridlayout.addWidget(self.pkg_inputEditText, 3, 1)

        # 第4行
        self.gridlayout.addWidget(self.activityClassPath, 4, 1)
        self.gridlayout.addWidget(self.action_start, 4, 0)

        # self.gridGroupBox.setLayout(self.gridlayout)

    def createActionsGroupView(self):
        # self.actionGroupBox = QGroupBox("Grid layout")
        self.actionLayout.addWidget(self.action_clear, alignment=Qt.AlignLeft)
        self.actionLayout.addWidget(self.action_uninstall, alignment=Qt.AlignLeft)
        self.actionLayout.addWidget(self.action_stop, alignment=Qt.AlignLeft)
        self.actionLayout.setSpacing(10)
        self.actionLayout.addStretch()
        # self.actionGroupBox.setLayout(self.gridlayout)

    def initViews(self):
        """
        初始化布局View
        :return:
        """
        # self.ipTipsTextView.setGeometry(QtCore.QRect(0, 0, 111, 31))
        # self.ipTipsTextView.setFont(getKTFontStyle())
        # self.ipTipsTextView.setTextFormat(QtCore.Qt.AutoText)
        # self.ipTipsTextView.setScaledContents(True)
        # self.ipTipsTextView.setObjectName("ipChoiceTips")

        self.ipComboBox.setGeometry(QtCore.QRect(0, 0, 181, 31))
        self.ipComboBox.setObjectName("ipComboBox")
        self.ipComboBox.setFont(getWRYHFontStyle())
        self.initIpData()
        self.ipComboBox.activated[str].connect(lambda: self.onIpComBoxSelected())


        #
        self.linkImageStateView.setGeometry(QtCore.QRect(0, 0, 71, 31))
        # self.linkImageStateView.setStyleSheet("background:rgb(164,111,255)")
        self.linkImageStateView.setObjectName("linkedView")

        # 包名提示&选择
        self.pkg_Tip.setGeometry(QtCore.QRect(0, 0, 111, 41))
        self.pkg_Tip.setFont(getKTFontStyle())
        self.pkg_Tip.setTextFormat(QtCore.Qt.AutoText)
        self.pkg_Tip.setScaledContents(True)
        self.pkg_Tip.setObjectName("pkgChoiceTips")

        self.pkgComboBox.setGeometry(QtCore.QRect(0, 0, 261, 31))
        self.pkgComboBox.setObjectName("pkgComboBoxView")
        self.pkgComboBox.setFont(getWRYHFontStyle())
        self.selected_pkg = self.pkgComboBox.currentText()
        self.pkg_inputEditText.setGeometry(QtCore.QRect(0, 0, 261, 31))
        self.pkg_inputEditText.setObjectName("package_add")
        self.pkg_inputEditText.setFont(getWRYHFontStyle(12))

        # 旧版consoleView
        # self.consoleView.setGeometry(QtCore.QRect(0, 0, 864, 210))
        # self.consoleView.setStyleSheet("background:rgb(128,128,128)")
        # self.consoleView.setTextColor(QColor('yellow'))
        # self.consoleView.setLineWrapMode(QTextEdit.NoWrap)  # 保持换行单词完整
        # self.consoleView.setFontPointSize(13)

        self.activityClassPath.setGeometry(QtCore.QRect(0, 0, 291, 31))
        self.activityClassPath.setObjectName("class_path")
        self.activityClassPath.setFont(getWRYHFontStyle(12))

        self.initPkgActionButtons()

    def initPkgActionButtons(self):
        """
        初始化应用行为操作按钮控件
        :return:
        """
        self.action_start.setGeometry(QtCore.QRect(0, 0, 81, 31))
        self.action_start.setObjectName("act_start")
        self.action_start.setFont(getWRYHFontStyle())
        self.action_start.clicked.connect(
            lambda: self.invokePkgAction('am start -n', "/" + self.activityClassPath.text()))

        self.action_clear.setGeometry(QtCore.QRect(0, 0, 81, 31))
        self.action_clear.setObjectName("act_clear")
        self.action_clear.setFont(getWRYHFontStyle())
        self.action_clear.clicked.connect(lambda: self.invokePkgAction('pm clear'))

        self.action_uninstall.setGeometry(QtCore.QRect(0, 0, 71, 31))
        self.action_uninstall.setObjectName("act_uninstall")
        self.action_uninstall.setFont(getWRYHFontStyle())
        self.action_uninstall.clicked.connect(lambda: self.invokePkgAction('pm uninstall'))

        self.action_stop.setGeometry(QtCore.QRect(0, 0, 71, 31))
        self.action_stop.setObjectName("act_stop")
        self.action_stop.setFont(getWRYHFontStyle())
        self.action_stop.clicked.connect(lambda: self.invokePkgAction('am force stop'))

    def invokePkgAction(self, action='', params=''):
        currentPkgName = self.pkgComboBox.currentText()
        if currentPkgName:
            self.doAdbCmd(True, "{0} {1}{2} ".format(action, currentPkgName, params))

    def initIpData(self):
        """
        从数据库初始化ip数据信息
        :return:
        """
        # if not self.sqlHelper:
        #     print('init ip data failed !')
        #     return
        # self.sqlHelper.createIpTable()
        self.updateIpComBox()

    def updateIpComBox(self):
        """
        更新ipComBox数据显示
        :return:
        """
        # if not self.sqlHelper:
        #     return
        # cur = self.sqlHelper.queryData(table_name=self.sqlHelper.table_ip)
        # self.ipComboBox.clear()
        # for item in cur:
        #     if item:
        #         try:
        #             data = "{0}:{1}".format(item[0], item[1])
        #             self.ipComboBox.addItem(data)
        #         except Exception:
        #             continue
        # self.selected_ip = self.ipComboBox.currentText()
        self.onIpComBoxSelected()

    def onIpComBoxSelected(self):
        """
        ip选择框被选中的回调
        :return:
        """
        # self.selected_ip = self.ipComboBox.currentText()
        # log.d('on IpComBox Selected: ' + self.selected_ip)
        # status, result = Tools.exec_cmd('adb devices')
        # if status:
        #     isConnected = self.selected_ip in result
        #     OldUIManager.isDeviceConnected = isConnected
        #     self.changeConnectBtnState(not isConnected, isConnected)
        # else:
        log.e('onIpComBoxSelected.')

    def toast(self, msg):
        self.windows.statusBar().showMessage(" {0}".format(msg))

    def updatePkgComBox(self):
        """
        更新PkgComBox数据显示
        :return:
        """
        # if not self.sqlHelper:
        #     return
        # cur = self.sqlHelper.queryData(column=self.sqlHelper.pkg_column_name,
        #                                table_name=self.sqlHelper.table_package)
        # self.pkgComboBox.clear()
        # for item in cur:
        #     if item:
        #         self.pkgComboBox.addItem(item[0])
        self.selected_pkg = self.pkgComboBox.currentText()

    def DoIpAction(self, act):
        """
        进行设备进行连接或断开操作
        :param act:  adb的 action  connect / disconnect
        :return:
        """
        if not self.selected_ip:
            print("数据异常，无法连接")
            return
        cmd = "adb {0} {1}".format(act, self.selected_ip)
        log.d("cmd ---> " + cmd)
        status, result = Tools.exec_cmd(cmd)
        if status:
            if act == 'connect':
                self.changeConnectBtnState(False, True)
            else:
                self.changeConnectBtnState(True, False)

    @staticmethod
    def doAdbCmd(isShell=False, parms=None):
        if not OldUIManager.isDeviceConnected:
            log.d('devices disconnected, pass adb cmd.')
            return

        if isShell:
            cmd = "adb shell {0} ".format(parms)
        else:
            cmd = "adb {0} ".format(parms)
        status, msg = Tools.exec_cmd(cmd)
        if status:
            log.d(msg)
        else:
            log.e(msg)

    def changeConnectBtnState(self, conEnable, disConEnable):
        """
        切换连接按钮状态及显示图标
        :param conEnable:     连接按钮是否Enable
        :param disConEnable:  断开按钮是否Enable
        :return:
        """
        OldUIManager.isDeviceConnected = not conEnable
        if disConEnable:
            resType = 'Linked'
        else:
            resType = 'Unlink'
        pixMap = QPixmap(OldUIManager.resImages[resType])
        self.linkImageStateView.setPixmap(pixMap)

    def onPkgAdd(self):
        new_pkg = self.pkg_inputEditText.text()
        if not new_pkg:
            self.toast(" 请先添加有效包名 !!!")
            return
        print("addNewPkg() --- " + new_pkg)
        # self.sqlHelper.insertPackageRow(new_pkg)

    def updateConnectStateImg(self):
        # 更新连接状态图标
        pass
