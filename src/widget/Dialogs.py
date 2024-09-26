import concurrent.futures
import os
import sys
import threading
import zipfile

from PyQt5 import QtWidgets, QtCore, QtGui
from PyQt5.QtCore import Qt, pyqtSlot, pyqtSignal
from PyQt5.QtGui import QDropEvent, QPixmap
from PyQt5.QtWidgets import QLineEdit, QApplication, QLabel, QPushButton, QHBoxLayout, QFileDialog, QTextEdit, \
    QGroupBox, QMenu, QAction, QDesktopWidget, QWidget

from AppConfigManager import AppConfigManager
from src import settings
from src.logcat.log import z_logger
from src.settings import APP_VERSION
from src.widget.ScreenRecord import Record_Dialog
from src.DataBase import DBManager
from src.widget.BaseDialog import BaseDialog, DragDialog
from src.widget.CustomWidgets import DraggableLineEdit, HoverQLineEdit, HoverQTextEdit
from utils import Tools
from utils.Utils import FileUtils
from utils.Tools import getSimpleFontStyle, getWRYHFontStyle, getSongFontStyle
from utils.UITools import IconTool


class NewConnectDialog(BaseDialog):
    """
    新建连接的Dialog, 使用绝对布局
    """
    def __init__(self, window = None, block = None):
        # super(NewConnectDialog, self).__init__()
        super().__init__("新建连接")
        self.window = window
        self.block = block
        self.connectButton = QPushButton(self)
        self.inputEdit = QLineEdit(self)
        self.helpLabel = QLabel(self)
        self.ipLabel = QLabel(self)

        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setFixedSize(460, 150)
        # ip icon
        self.ipLabel.setPixmap(IconTool.buildQPixmap('ip.png'))
        self.ipLabel.move(40, 28)

        self.helpLabel.setPixmap(IconTool.buildQPixmap('help.png'))
        self.helpLabel.move(380, 28)
        self.helpLabel.setToolTip('''格式: ip[:adb port]
        default adb port is 5555
        EX: 192.168.200.2:5555''')

        self.inputEdit.move(80, 30)
        self.inputEdit.setPlaceholderText("目标设备ip")
        self.inputEdit.resize(290, 25)

        self.connectButton.setText('connect')
        self.connectButton.clicked.connect(self.onConnectClick)
        self.connectButton.setGeometry(150, 90, 120, 25)
        # root_layout.addLayout()
        # root_layout.addWidget(self.inputEdit)
        # self.setLayout(root_layout)

    @pyqtSlot()
    def onConnectClick(self):
        new_ip = self.inputEdit.text()
        if not new_ip:
            self.inputEdit.setPlaceholderText('请先输入ip地址！')
            return
        # IP格式校验
        match = Tools.isIpMatches(new_ip)
        if not match:
            self.setStatusTip('无效参数！请检查格式！')
            return
        if ":" not in new_ip:
            new_ip += ":5555"
        self.block(new_ip)
        self.close()


class AddPackageDialog(BaseDialog):
    """
    添加新包名的Dialog
    """
    def __init__(self, window = None, block = None):
        # super(NewConnectDialog, self).__init__()
        super().__init__("添加新应用")
        self.dbManager = DBManager()

        self.window = window
        self.block = block
        self.btnAddNewPkg = QPushButton(self)
        self.pkg_inputEditText = QLineEdit(self)

        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setFixedSize(550, 100)

        layout = QHBoxLayout(self)
        self.setLayout(layout)

        # self.pkg_inputEditText.move(50)
        self.pkg_inputEditText.setPlaceholderText("Input package name")
        # self.pkg_inputEditText.resize(290, )

        self.btnAddNewPkg.setText('Add')
        self.btnAddNewPkg.clicked.connect(lambda: self.on_package_clicked())
        layout.addWidget(self.pkg_inputEditText)
        layout.addWidget(self.btnAddNewPkg)

    def on_package_clicked(self):
        self.block(self.pkg_inputEditText.text())


class installApkDialog(BaseDialog):
    def __init__(self,  window = None):
        super().__init__("应用安装")
        self.mainWindow = window

        self.install_mode_options_layout = QtWidgets.QHBoxLayout()
        self.rb_downgrade = QtWidgets.QCheckBox(self)
        self.rb_test_app = QtWidgets.QCheckBox(self)
        self.rb_mode_replace = QtWidgets.QCheckBox(self)
        self.rb_mode_replace.setChecked(True)

        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setFixedSize(800, 150)

        self.root_layout = QtWidgets.QVBoxLayout(self)
        self.root_layout.setObjectName("root_layout")
        self.install_groupBox = QtWidgets.QGroupBox(self)
        self.install_groupBox.setObjectName("install_groupBox")
        self.verticalLayout = QtWidgets.QVBoxLayout(self.install_groupBox)
        self.verticalLayout.setObjectName("verticalLayout")

        # 文件选择区域
        self.file_choose_area = QtWidgets.QHBoxLayout()
        self.file_choose_area.setSpacing(5)
        self.file_choose_area.setObjectName("file_choose_area")

        self.file_path_edit_text = DraggableLineEdit(self.install_groupBox)
        self.file_path_edit_text.setObjectName("file_path_edit_text")
        self.file_path_edit_text.setPlaceholderText("可将apk文件直接拖入")
        self.file_path_edit_text.setDropEventListerner(lambda apk_path: self.btn_install_apk.setEnabled(True))
        self.file_choose_area.addWidget(self.file_path_edit_text)

        self.btn_choose_apk_file = QtWidgets.QPushButton(self.install_groupBox)
        self.btn_choose_apk_file.setObjectName("btn_choose_apk_file")
        self.btn_choose_apk_file.setObjectName("btn_choose_apk_file")
        self.btn_choose_apk_file.clicked.connect(self.onApkFileSelected)
        self.file_choose_area.addWidget(self.btn_choose_apk_file)

        self.btn_install_apk = QtWidgets.QPushButton(self.install_groupBox)
        self.btn_install_apk.setObjectName("btn_install_apk")
        self.btn_install_apk.setEnabled(False)
        self.btn_install_apk.clicked.connect(self.doInstallApk)
        self.file_choose_area.addWidget(self.btn_install_apk)
        self.file_choose_area.setStretch(0, 8)
        self.file_choose_area.setStretch(1, 1)
        self.file_choose_area.setStretch(2, 1)
        self.verticalLayout.addLayout(self.file_choose_area)

        # 安装模式区域
        self.install_mode_options_layout.setObjectName("horizontalLayout_2")
        self.rb_mode_replace.setObjectName("rb_mode_replace")
        self.install_mode_options_layout.addWidget(self.rb_mode_replace)
        self.rb_test_app.setObjectName("rb_test_app")
        self.install_mode_options_layout.addWidget(self.rb_test_app)
        self.rb_downgrade.setObjectName("rb_downgrade")
        self.install_mode_options_layout.addWidget(self.rb_downgrade)
        spacerItem = QtWidgets.QSpacerItem(40, 20, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Minimum)
        self.install_mode_options_layout.addItem(spacerItem)
        self.verticalLayout.addLayout(self.install_mode_options_layout)

        # 垂直方向上添加一个安装的groupBox
        self.root_layout.addWidget(self.install_groupBox)

        self.retranslateUi(self)
        QtCore.QMetaObject.connectSlotsByName(self)

    def retranslateUi(self, Dialog):
        _translate = QtCore.QCoreApplication.translate
        self.install_groupBox.setTitle(_translate("install_groupBox", "安装应用"))
        self.btn_choose_apk_file.setText(_translate("btn_choose_apk_file", "浏览"))
        self.btn_install_apk.setText(_translate("btn_install_apk", "安装"))
        self.rb_mode_replace.setText(_translate("rb_mode_replace", "替换安装"))
        self.rb_test_app.setText(_translate("rb_test_app", "Test包"))
        self.rb_downgrade.setText(_translate("rb_downground", "降级安装"))

    def onApkFileSelected(self):
        # 打开文件选择对话框
        fname = QFileDialog.getOpenFileName(self, '选择文件', '.', 'APK File (*.apk)')
        if fname[0]:  # 如果用户选择了文件
            # 将文件路径写入 QLineEdit 控件中
            self.file_path_edit_text.setText(fname[0])
            self.btn_install_apk.setEnabled(True)

    def doInstallApk(self):
        _cmd = f"adb -s {self.mainWindow.current_device_addr} install "
        # 进行APK文件安装
        apkPath = self.file_path_edit_text.text()
        if self.rb_mode_replace.isChecked():  # -r
            _cmd += "-r "
        if self.rb_test_app.isChecked():      # -t
            _cmd += "-t "
        if self.rb_downgrade.isChecked():    # -d
            _cmd += "-d "
        _cmd += apkPath
        self.mainWindow.adbTools.async_exec_adb_cmd([_cmd])
        z_logger.info("Installing........")


class screen_record_dialog(BaseDialog):
    def __init__(self,  window = None):
        super().__init__("屏幕录制")
        self.mainWindow = window
        self.record_dialog = Record_Dialog()
        self.initWindow()

    def initWindow(self):
        super().initWindow()
        self.record_dialog.setupUi(self, self.mainWindow)


class device_alis_edit_dialog(BaseDialog):
    on_alias_update_signal = pyqtSignal(str)
    """
    设备别名编辑对话框
    """
    def __init__(self,  window = None):
        super().__init__("备注修改")
        self.mainWindow = window
        self.initWindow()

    def initWindow(self):
        super().initWindow()
        self.setObjectName("text_input")
        self.resize(374, 65)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.sizePolicy().hasHeightForWidth())
        self.setSizePolicy(sizePolicy)
        self.verticalLayout = QtWidgets.QVBoxLayout(self)
        self.verticalLayout.setObjectName("verticalLayout")
        self.tips_label = QtWidgets.QLabel(self)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.tips_label.sizePolicy().hasHeightForWidth())
        self.tips_label.setSizePolicy(sizePolicy)
        self.tips_label.setObjectName("tips_label")
        self.verticalLayout.addWidget(self.tips_label)
        self.lineedit_text = QtWidgets.QLineEdit(self)
        self.lineedit_text.setObjectName("lineedit_text")
        self.verticalLayout.addWidget(self.lineedit_text)
        self.btn_input_text = QtWidgets.QPushButton(self)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.btn_input_text.sizePolicy().hasHeightForWidth())
        self.btn_input_text.setSizePolicy(sizePolicy)
        self.btn_input_text.setToolTipDuration(0)
        self.btn_input_text.setObjectName("btn_input_text")
        self.btn_input_text.clicked.connect(self._on_name_update)
        self.verticalLayout.addWidget(self.btn_input_text)

        self.retranslateUi(self)
        QtCore.QMetaObject.connectSlotsByName(self)

    def retranslateUi(self, text_input):
        _translate = QtCore.QCoreApplication.translate
        text_input.setWindowTitle(_translate("text_input", "备注修改"))
        self.tips_label.setText(_translate("text_input", "请输入设备别名，便于识别:"))
        self.btn_input_text.setText(_translate("text_input", "更新备注名称"))

    def _on_name_update(self):
        alis_name = self.lineedit_text.text()
        if len(str(alis_name)) > 0:
            self.mainWindow.pkgManager.updateDeviceAlias(self.mainWindow.current_device_addr.split(":")[0], alis_name)
            self.on_alias_update_signal.emit(alis_name)
            self.accept()


class AboutDialog(BaseDialog):
    def __init__(self,  window = None):
        super().__init__("关于")
        self.mainWindow = window
        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setFixedSize(300, 100)
        self.verticalLayout_2 = QtWidgets.QVBoxLayout(self)
        self.verticalLayout_2.setObjectName("verticalLayout_2")
        self.label = QtWidgets.QLabel(self)
        font = QtGui.QFont()
        font.setPointSize(22)
        font.setBold(True)
        font.setWeight(75)
        self.label.setFont(font)
        self.label.setAlignment(QtCore.Qt.AlignCenter)
        self.label.setObjectName("label")
        self.verticalLayout_2.addWidget(self.label)

        self.retranslateUi(self)
        QtCore.QMetaObject.connectSlotsByName(self)

    def retranslateUi(self, Dialog):
        _translate = QtCore.QCoreApplication.translate
        Dialog.setWindowTitle(_translate("Dialog", "关于"))
        self.label.setText(_translate("Dialog", f"v{APP_VERSION}"))


class TextInputDialog(BaseDialog):
    def __init__(self,  window = None):
        super().__init__("文本输入")
        self.mainWindow = window
        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setObjectName("self")
        self.resize(650, 35)
        self.setMaximumHeight(40)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.sizePolicy().hasHeightForWidth())
        self.setSizePolicy(sizePolicy)
        self.horizontalLayout = QtWidgets.QHBoxLayout(self)
        self.horizontalLayout.setObjectName("horizontalLayout")
        self.lineedit_text = QtWidgets.QLineEdit(self)
        self.lineedit_text.setObjectName("lineedit_text")
        self.horizontalLayout.addWidget(self.lineedit_text)
        self.btn_input_text = QtWidgets.QPushButton(self)
        self.btn_input_text.clicked.connect(self.doTextInput)

        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Fixed)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.btn_input_text.sizePolicy().hasHeightForWidth())
        self.btn_input_text.setSizePolicy(sizePolicy)
        self.btn_input_text.setObjectName("btn_input_text")
        self.horizontalLayout.addWidget(self.btn_input_text)
        self.horizontalLayout.setStretch(0, 4)
        self.horizontalLayout.setStretch(1, 1)

        self.retranslateUi()
        QtCore.QMetaObject.connectSlotsByName(self)

    def retranslateUi(self):
        _translate = QtCore.QCoreApplication.translate
        # self.setWindowTitle(_translate("self", "Dialog"))
        self.btn_input_text.setText(_translate("self", "输入"))

    def doTextInput(self):
        _text = self.lineedit_text.text()
        if _text:
            self.mainWindow.adbTools.exec_adb_cmd(f"adb -s {self.mainWindow.current_device_addr} shell input text {_text}")


class APKHelperDialog(DragDialog):
    # 解析完毕的信号
    parseFinished = pyqtSignal(dict)

    config_manager = AppConfigManager('../../config/AppConfig.ini')

    """
    APK 解析器弹窗
    """
    def __init__(self,  window=None):
        super().__init__("APK Helper")
        self.setStyleSheet("""
            DragDialog {
                "background-color: rgb(245, 245, 245);"
            }
            """
        )

        self.apkParsePool = concurrent.futures.ThreadPoolExecutor(max_workers=2)
        self.parseFinished.connect(self.updateUIOnParsed)

        self.mainWindow = window
        self.initWindow()

        # 弹窗整体垂直
        self.rootVLayout = QtWidgets.QVBoxLayout(self)
        self.rootVLayout.setObjectName("v_apkhelper_root")
        self.rootVLayout.setContentsMargins(6, 6, 6, 6)
        self.rootVLayout.setSpacing(3)

        self.file_path = ""
        # apk信息的GroupBox
        self.apkInfoGroupBox = QtWidgets.QGroupBox()
        # apk包名View
        self.apkPkgView = None
        # 状态信息提示
        self.status_label = None
        # 文件信息的GroupBox
        self.fileInfoGroupBox = QtWidgets.QGroupBox()
        self.initViews()

    def initWindow(self):
        self.setWindowTitle(self.title)
        self.setWindowIcon(IconTool.buildQIcon("apk_64x64_09a413.png", dir="icons"))
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowFullscreenButtonHint)
        # 获取屏幕大小
        screen = QDesktopWidget().screenGeometry()
        screen_width = screen.width()
        screen_height = screen.height()

        # 自定义新窗口大小
        dialog_width = int(screen_width * 0.28)
        dialog_height = int(screen_height * 0.65)

        self.setMinimumSize(dialog_width, dialog_height)
        self.setMaximumSize(int(dialog_width * 1.5), int(dialog_height * 1.5))
        self.resize(dialog_width, dialog_height)
        self.center()

    def center(self):
        """将对话框居中在其父窗口所在的屏幕上"""
        # Dialog整体形状对象
        qr = self.frameGeometry()
        if self.mainWindow is not None:
            cp = self.mainWindow.window().screen().availableGeometry().center()
            qr.moveCenter(cp)
            self.move(qr.topLeft())

    def initViews(self):
        self.initApkInfoView()
        self.initFileInfoView()

        # 状态栏
        self.status_label = QLabel('拖文件到窗口即可检查apk信息')
        self.status_label.setAlignment(Qt.AlignCenter)
        # self.status_label.setStyleSheet("padding: 2px;")
        self.rootVLayout.addWidget(self.status_label)


    def initApkInfoView(self):
        """
        创建APK信息的分组Box
        :return: None
        """
        self.apkInfoGroupBox.setFont(getSimpleFontStyle())
        self.apkInfoGroupBox.setObjectName("app_info_group")
        self.apkInfoGroupBox.setTitle("APK信息")
        self.apkInfoGroupBox.setStyleSheet("""
            QGroupBox {
                background-color: white;
                font-weight: bold; 
                border: 2px solid rgb(231, 231, 231);
            }
            QGroupBox::title {
                subcontrol-origin: margin;
                left: 8px;
                padding: 0 3px 0 3px;
            }
        """)

        self.appInfoGridLayout = QtWidgets.QGridLayout(self.apkInfoGroupBox)
        self.appInfoGridLayout.setObjectName("grid_layout_of_app_info")

        # 包名、名称、证书MD5布局
        topLines = ['包名',
                    '名称',
                    '@应用特征信息@',
                    '启动入口',
                    '证书MD5',
                    '签名版本',
                    'App版本号',
                    '代码版本号',
                    'Min.SDK',
                    '权限要求'
                    ]

        for index, name in enumerate(topLines):

            if name == "@应用特征信息@":
                hRootWidget = QWidget()
                layout = QHBoxLayout(hRootWidget)
                layout.setContentsMargins(0, 0, 0, 0)
                layout.setSpacing(5)

                _attrs = ['Launcher应用', 'Icon展示', '系统应用']
                questionIcon = IconTool.buildQPixmap(path='../..', pixmapName="help.png")
                for attr in _attrs:
                    labelView: QLabel = self.getInfoLabel(f"{attr}:", f"apkAtts_of_{attr}", self.apkInfoGroupBox)
                    value_Label: QLabel = QLabel()
                    value_Label.setObjectName(f"obj_appAttrs_of_{attr}")
                    value_Label.setFixedSize(20, 20)
                    value_Label.setScaledContents(True)
                    value_Label.setPixmap(questionIcon)
                    layout.addWidget(labelView)
                    layout.addWidget(value_Label)
                layout.addStretch()
                self.appInfoGridLayout.addWidget(hRootWidget, index, 0, 1, 4)
                continue

            labelView: QLabel = self.getInfoLabel(name, f"apkinfo_{index}", self.apkInfoGroupBox)
            self.appInfoGridLayout.addWidget(labelView, index, 0)

            if name == '权限要求':
                lineEdit: QTextEdit = HoverQTextEdit(self.apkInfoGroupBox)
            else:
                lineEdit: QLineEdit = HoverQLineEdit(self.apkInfoGroupBox)
            lineEdit.setObjectName(f"obj_appInfo_at_{name}")
            # lineEdit.setPlaceholderText(f"占_{name}")
            lineEdit.setFont(getWRYHFontStyle(9))
            lineEdit.setReadOnly(True)

            self.appInfoGridLayout.addWidget(labelView, index, 0)
            if name == 'App版本号' or name == '代码版本号' or name == 'Min.SDK':
                self.appInfoGridLayout.addWidget(lineEdit, index, 1, 1, 3)
                if name == 'App版本号':
                    self.logoImage: QLabel = self.getInfoLabel("logo", "obj_logo", self.apkInfoGroupBox)
                    self.logoImage.setFixedSize(64, 64)
                    self.logoImage.setScaledContents(True)  # 图片自适应QLabel大小
                    self.logoImage.setStyleSheet(
                        """
                        padding: 2px;
                        min-height: 64px;
                        min-width: 64px;
                        alignment: bottom center;  /* 底部居中 */
                        """
                    )
                    # 添加自定义上下文菜单
                    self.logoImage.setContextMenuPolicy(Qt.CustomContextMenu)
                    self.logoImage.customContextMenuRequested.connect(self.showIconContextMenu)

                    # 添加logo控件，行数同'App版本号'          (在index行, 第5列, 跨2行, 占1列，居中)
                    self.appInfoGridLayout.addWidget(self.logoImage, index, 4, 2, 1, Qt.AlignmentFlag.AlignCenter)
                elif name == 'Min.SDK':
                    logoInfo: QLabel = self.getInfoLabel("", "obj_logo_info", self.apkInfoGroupBox)
                    self.appInfoGridLayout.addWidget(logoInfo, index, 4, 1, 1, Qt.AlignmentFlag.AlignCenter)

            elif name == '权限要求':
                lineEdit.setFont(getWRYHFontStyle(9))
                sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
                sizePolicy.setHorizontalStretch(0)
                sizePolicy.setVerticalStretch(0)
                sizePolicy.setHeightForWidth(lineEdit.sizePolicy().hasHeightForWidth())
                lineEdit.setSizePolicy(sizePolicy)
                self.appInfoGridLayout.addWidget(lineEdit, index, 1, 2, 4)
            else:
                self.appInfoGridLayout.addWidget(lineEdit, index, 1, 1, 4)

        self.apkInfoGroupBox.setLayout(self.appInfoGridLayout)
        self.rootVLayout.addWidget(self.apkInfoGroupBox)

    def initFileInfoView(self):
        """
        创建文件信息的分组Box
        :return:
        """
        self.fileInfoGroupBox.setFont(getSimpleFontStyle())
        self.fileInfoGroupBox.setObjectName("file_info_group")
        self.fileInfoGroupBox.setTitle("文件信息")
        self.fileInfoGroupBox.setStyleSheet("""
            QGroupBox {
                background-color: white;
                font-weight: bold; 
                border: 2px solid rgb(231, 231, 231);
            }
            QGroupBox::title {
                subcontrol-origin: margin;
                left: 8px;
                padding: 0 3px 0 3px;
            }
        """
        )

        fileInfoGridLayout = QtWidgets.QGridLayout(self.fileInfoGroupBox)
        fileInfoGridLayout.setObjectName("grid_layout_of_app_info")

        rows = ['文件名', 'MD5', '大小', '日期']
        for index, name in enumerate(rows):
            labelView: QLabel = self.getInfoLabel(name, f"fileinfo_{index}", self.fileInfoGroupBox)
            lineEdit: QLineEdit = HoverQLineEdit(self.fileInfoGroupBox)
            lineEdit.setObjectName(f"obj_fileInfo_at_{index}")
            # lineEdit.setPlaceholderText(f"占位数据_{name}")
            lineEdit.setFont(getWRYHFontStyle(9))
            lineEdit.setReadOnly(True)
            fileInfoGridLayout.addWidget(labelView, index, 0)
            fileInfoGridLayout.addWidget(lineEdit, index, 1, 1, 4)

        self.fileInfoGroupBox.setLayout(fileInfoGridLayout)
        self.rootVLayout.addWidget(self.fileInfoGroupBox)
        self.rootVLayout.addStretch(1)

    def __startApkParse(self):
        z_logger.info(f"Start parse apk: {self.file_path}")
        """
        开始进行APK解析，并处理数据格式的转换
        :return:
        """
        result = FileUtils.parse_apk(self.file_path)
        if not result["success"]:
            # Fast failure
            self.parseFinished.emit(result)
            return

        # 拼接签名版本
        if len(result['sign_md5_version']) > 0:
            result['sign_md5_version'] = (",".join(result['sign_md5_version']))
        if len(result['permissions']) > 0:
            permission_chinese = []
            for p in result['permissions']:
                if not p.startswith("android.permission"):  # 过滤自定义权限
                    continue
                permission_name = p.split('.')[-1]
                # permission_name = self.config_manager.permissions(item)
                permission_chinese.append(permission_name)

            result['permissions'] = "- " + ('\r\n- '.join(permission_chinese))

        result['file_md5'] = result['file_md5'].upper()
        # 处理文件大小显示
        file_size = result['file_bytes']
        file_size_mb = file_size / 1024 / 1024
        file_size_bytes_format = "{:,}".format(file_size)
        result['file_bytes'] = f"{file_size_bytes_format} 字节({file_size_mb:.2f} MB)"

        # 解析图片icon
        icon = self.extract_icon(result['icon_path'])
        result['icon_path'] = icon

        # 通过信号机制发出数据
        self.parseFinished.emit(result)

    def extract_icon(self, icon_path):
        """
        从APK文件中抽离图标
        :param icon_path: /res/drawable/ic_launcher.png
                        注意，是反斜杠的路径
        :return:
        """
        if len(icon_path) == 0:
            return ""

        tem_icon_root_path = os.path.join(settings.appTempPath, "icon")
        # 清除icon目录下的缓存图片
        if os.path.exists(tem_icon_root_path):
            FileUtils.clear_directory(tem_icon_root_path)

        # 打开 APK 文件，释放出目标文件
        with zipfile.ZipFile(self.file_path, 'r') as zip_ref:
            zip_ref.extract(icon_path, tem_icon_root_path)  # 提取图标文件

        extract_icon_path = os.path.join(tem_icon_root_path, icon_path.replace("/", "\\"))
        # z_logger.info(f"Extract icon to :{extract_icon_path}")
        return extract_icon_path

    @pyqtSlot(dict)
    def updateUIOnParsed(self, apkFileInfo):
        if not apkFileInfo["success"]:
            self.status_label.setStyleSheet("QLabel {color: red; }")
            self.status_label.setText(apkFileInfo["reason"])
            return
        self.status_label.setStyleSheet("QLabel {color: black; }")
        self.status_label.setText("拖文件到窗口即可检查apk信息")
        z_logger.info("Parsed success! updateUI")

        # App 特征属性
        isLauncherAppView = self.apkInfoGroupBox.findChild(QLabel, "obj_appAttrs_of_Launcher应用")
        isShowIconView = self.apkInfoGroupBox.findChild(QLabel, "obj_appAttrs_of_Icon展示")
        isSystemAppView = self.apkInfoGroupBox.findChild(QLabel, "obj_appAttrs_of_系统应用")
        _greenPixMap = IconTool.buildQPixmap(path='../..', pixmapName="state_connect_normal.png")
        _redPixMap = IconTool.buildQPixmap(path='../..', pixmapName="state_disconnect.png")
        isLauncherAppView.setPixmap(_greenPixMap if apkFileInfo['is_launcher_app'] else _redPixMap)
        isShowIconView.setPixmap(_greenPixMap if apkFileInfo['is_show_launch_icon'] else _redPixMap)
        isSystemAppView.setPixmap(_greenPixMap if apkFileInfo['is_sys_app'] else _redPixMap)

        # App 常用信息
        self.apkPkgView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_包名")
        apkNameView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_名称")
        entryActivityView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_启动入口")
        signMd5View = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_证书MD5")
        signMd5VerView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_签名版本")
        apkVersionNameView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_App版本号")
        apkVersionCodeView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_代码版本号")
        apkMinSDKView = self.apkInfoGroupBox.findChild(QLineEdit, "obj_appInfo_at_Min.SDK")
        apkPermissionsView = self.apkInfoGroupBox.findChild(QTextEdit, "obj_appInfo_at_权限要求")

        self.apkPkgView.setText(apkFileInfo['package_name'])
        apkNameView.setText(apkFileInfo['app_name'])
        entryActivityView.setText(apkFileInfo['launchable_activity'])
        signMd5View.setText(apkFileInfo['sign_md5'])
        signMd5VerView.setText(apkFileInfo['sign_md5_version'])
        apkVersionNameView.setText(apkFileInfo['version_name'])
        apkVersionCodeView.setText(apkFileInfo['version_code'])
        apkMinSDKView.setText(apkFileInfo['min_sdk'])
        if len(apkFileInfo['permissions']) > 0:
            apkPermissionsView.setText(apkFileInfo['permissions'])

        # App Icon Logo
        appLogoView = self.apkInfoGroupBox.findChild(QLabel, "obj_logo")
        appLogoInfoView = self.apkInfoGroupBox.findChild(QLabel, "obj_logo_info")
        _iconPath = apkFileInfo['icon_path']
        if _iconPath and _iconPath.strip():
            pixMap = QPixmap(_iconPath)
            original_width = pixMap.width() # 获取图片的原始尺寸
            original_height = pixMap.height()
            size_info = f'{original_width}x{original_height}'
            appLogoView.setPixmap(pixMap)
            appLogoInfoView.setText(size_info)

        # 文件信息
        fileNameView = self.fileInfoGroupBox.findChild(QLineEdit, "obj_fileInfo_at_0")
        fileMd5View = self.fileInfoGroupBox.findChild(QLineEdit, "obj_fileInfo_at_1")
        fileSizeView = self.fileInfoGroupBox.findChild(QLineEdit, "obj_fileInfo_at_2")
        fileDateView = self.fileInfoGroupBox.findChild(QLineEdit, "obj_fileInfo_at_3")
        fileNameView.setText(apkFileInfo['file_name'])
        fileMd5View.setText(apkFileInfo['file_md5'].upper())
        fileSizeView.setText(apkFileInfo['file_bytes'])
        fileDateView.setText(apkFileInfo['last_modified'])

    def getInfoLabel(self, name: str, objName:str, parent: QGroupBox):
        """
        获取统一UI样式的Label控件
        :param name:  文字
        :param parent:  所在的
        :return:
        """
        labelView = QtWidgets.QLabel(parent)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(labelView.sizePolicy().hasHeightForWidth())
        labelView.setSizePolicy(sizePolicy)
        labelView.setObjectName(objName)
        labelView.setAlignment(QtCore.Qt.AlignCenter)
        labelView.setText(name)
        labelView.setFont(getWRYHFontStyle())
        return labelView

    def showIconContextMenu(self, pos):
        if self.apkPkgView and (len(self.apkPkgView.text()) > 0):
            # 创建一个右键菜单
            menu = QMenu(self)
            saveAction = QAction('保存图标', self)
            saveAction.triggered.connect(self.saveIconImage)
            menu.addAction(saveAction)
            # 显示菜单
            menu.exec_(self.logoImage.mapToGlobal(pos))

    def saveIconImage(self):
        # 获取用户的下载目录
        downloads_path = os.path.join(os.environ['USERPROFILE'], 'Downloads')
        if not os.path.exists(downloads_path): # 若不存在，则获取桌面路径
            downloads_path = os.path.join(os.environ['USERPROFILE'], 'Desktop')
        pkgName = self.apkPkgView.text()
        pixmap = self.logoImage.pixmap()
        directory_path = os.path.join(downloads_path, f"{pkgName}_icon_{pixmap.width()}x{pixmap.height()}")

        options = QFileDialog.Options()
        selectedFile, _ = QFileDialog.getSaveFileName(self, "保存图片", directory_path, "图片文件 (*.png *.jpg *.bmp)", options=options)
        if selectedFile:
            # 保存图片
            self.logoImage.pixmap().save(selectedFile)

    def dropEvent(self, event: QDropEvent):
        super().dropEvent(event)

        # 获取拖放的文件(QUrl)列表
        urls = event.mimeData().urls()
        if urls:
            # # 遍历所有拖入的文件 QUrl对象
            # for url in urls:
            self.status_label.setText("解析中....")
            self.file_path = urls[0].toLocalFile() # 本地文件路径
            self.apkParsePool.submit(self.__startApkParse)


if __name__ == "__main__":
    app = QApplication(sys.argv)
    dialog = APKHelperDialog()
    # 设置窗口的属性为ApplicationModal模态，用户只有关闭弹窗后，才能关闭主界面
    dialog.setWindowModality(Qt.ApplicationModal)
    dialog.show()
    sys.exit(app.exec_())



