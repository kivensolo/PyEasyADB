import concurrent.futures
import os
import sys
import webbrowser
import zipfile

from PyQt5 import QtWidgets, QtCore, QtGui
from PyQt5.QtCore import Qt, pyqtSlot, pyqtSignal, QEvent
from PyQt5.QtGui import QDropEvent, QPixmap
from PyQt5.QtWidgets import QLineEdit, QApplication, QLabel, QPushButton, QHBoxLayout, QFileDialog, QTextEdit, \
    QGroupBox, QMenu, QAction, QDesktopWidget, QWidget

from AppConfigManager import AppConfigManager
from src import settings
from src.DataBase import DBManager
from src.logcat.log import z_logger
from src.settings import APP_VERSION, COMMON_CONFIG_FILE_PATH
from src.widget.BaseDialog import BaseDialog, DragDialog
from src.widget.CustomWidgets import DraggableLineEdit, HoverQLineEdit, HoverQTextEdit
from src.widget.ScreenRecord import Record_Dialog
from utils import Tools
from utils.Tools import getSimpleFontStyle, getWRYHFontStyle
from utils.UITools import IconTool, UiUtils
from utils.Utils import FileUtils


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

    def changeEvent(self, event):
        event_type = event.type()
            # self.adjustSizeForCurrentScreen()
        super().changeEvent(event)

    def adjustSizeForCurrentScreen(self):
        screen = self.screen()
        dpi = screen.logicalDotsPerInch()
        scale_factor = dpi / 96.0
        self.resize(int(460 * scale_factor), int(460 * scale_factor))

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

        self.connectButton.move(150, 90)
        self.connectButton.setText('connect')
        self.connectButton.clicked.connect(self.onConnectClick)
        self.connectButton.resize(120, 25)
        # root_layout.addLayout()
        # root_layout.addWidget(self.inputEdit)
        # self.setLayout(root_layout)
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        # self.setWindowFlags(Qt.WindowCloseButtonHint)
        # self.setFixedSize(UiUtils.getScaleWidth(460), UiUtils.getScaleHeight(150))
        # # ip icon
        # self.ipLabel.setPixmap(IconTool.buildQPixmap('ip.png'))
        # self.ipLabel.move(UiUtils.getScaleWidth(40), UiUtils.getScaleHeight(28))
        #
        # self.helpLabel.setPixmap(IconTool.buildQPixmap('help.png'))
        # self.helpLabel.move(UiUtils.getScaleWidth(380), UiUtils.getScaleHeight(28))
        # self.helpLabel.setToolTip('''格式: ip[:adb port]
        # default adb port is 5555
        # EX: 192.168.200.2:5555''')
        #
        # self.inputEdit.move(UiUtils.getScaleWidth(80), UiUtils.getScaleHeight(30))
        # self.inputEdit.setPlaceholderText("目标设备ip")
        # self.inputEdit.resize(UiUtils.getScaleWidth(290), UiUtils.getScaleHeight(25))
        #
        # self.connectButton.setText('connect')
        # self.connectButton.clicked.connect(self.onConnectClick)
        # self.connectButton.setGeometry(
        #     UiUtils.getScaleWidth(150),
        #     UiUtils.getScaleHeight(90),
        #     UiUtils.getScaleWidth(120),
        #     UiUtils.getScaleHeight(25)
        # )

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
    """
    关于对话框
    """
    # 项目信息配置
    PROJECT_NAME = "EasyADB"
    GITHUB_URL = "https://github.com/kivensolo/PyEasyADB"

    def __init__(self, window=None):
        super().__init__("关于")
        self.mainWindow = window
        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)

        # 根据 DPI 缩放调整窗口宽度（高度自适应）
        base_width = 400
        window_width = min(UiUtils.getScaleWidth(base_width), 500)
        self.setFixedWidth(window_width)

        # 创建主布局
        self.mainLayout = QtWidgets.QVBoxLayout(self)
        self.mainLayout.setContentsMargins(
            UiUtils.getScaleWidth(20),
            UiUtils.getScaleHeight(20),
            UiUtils.getScaleWidth(20),
            UiUtils.getScaleHeight(20)
        )

        # 按顺序从上往下添加组件
        self.initAppTitle()
        self.initVersionNumber()
        self.initBuildInfo()
        self.initGithubLink()

        # 设置背景样式
        self.setStyleSheet("QDialog{background: white;}")

        # 调整窗口大小以适应内容
        self.adjustSize()

    def initAppTitle(self):
        """应用标题"""
        self.titleLabel = QtWidgets.QLabel()
        self.titleLabel.setAlignment(Qt.AlignCenter)
        self.titleLabel.setStyleSheet("QLabel{background: black; color: white; padding: 10px;}")
        self.titleLabel.setText(self.PROJECT_NAME)

        # 字体设置
        font = QtGui.QFont()
        font.setPointSize(UiUtils.getScaleValue(26))
        font.setBold(True)
        self.titleLabel.setFont(font)

        # 标题保留固定高度（因为有黑色背景样式）
        self.titleLabel.setFixedHeight(UiUtils.getScaleHeight(70))
        self.mainLayout.addWidget(self.titleLabel)

    def initVersionNumber(self):
        """版本号"""
        self.versionLabel = QtWidgets.QLabel()
        self.versionLabel.setAlignment(Qt.AlignCenter)
        self.versionLabel.setText(f"版本号：v{APP_VERSION}")

        # 字体设置
        font = QtGui.QFont()
        font.setPointSize(UiUtils.getScaleValue(10))
        self.versionLabel.setFont(font)

        self.mainLayout.addWidget(self.versionLabel)

    def initGithubLink(self):
        """GitHub 链接按钮"""
        self.githubButton = QtWidgets.QPushButton()
        self.githubButton.setCursor(Qt.PointingHandCursor)
        self.githubButton.setStyleSheet("""
            QPushButton {
                background: transparent;
                color: blue;
                border: none;
                text-align: center;
            }
            QPushButton:hover {
                text-decoration: underline;
            }
        """)
        self.githubButton.setText("GitHub/EasyPyADB")
        self.githubButton.clicked.connect(self.openGithubUrl)

        # 字体设置
        font = QtGui.QFont()
        font.setPointSize(UiUtils.getScaleValue(10))
        self.githubButton.setFont(font)

        self.mainLayout.addWidget(self.githubButton)

    def initBuildInfo(self):
        """编译信息"""
        # 获取当前可执行文件的修改时间作为编译时间
        import os
        import time
        build_time = "Unknown"
        if hasattr(sys, 'frozen'):
            # 打包后的exe
            build_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(os.path.getmtime(sys.executable)))
        else:
            # 开发环境
            build_time = time.strftime('%Y-%m-%d %H:%M:%S', time.localtime(os.path.getmtime(__file__)))

        self.buildInfoLabel = QtWidgets.QLabel()
        self.buildInfoLabel.setAlignment(Qt.AlignCenter)
        self.buildInfoLabel.setText(f"编译时间：{build_time}")

        # 字体设置（稍小一些）
        font = QtGui.QFont()
        font.setPointSize(UiUtils.getScaleValue(8))
        self.buildInfoLabel.setFont(font)

        self.mainLayout.addWidget(self.buildInfoLabel)

    def openGithubUrl(self):
        """打开 GitHub 链接"""
        webbrowser.open(self.GITHUB_URL)


class PullApkDialog(BaseDialog):
    """
    提取设备已安装应用 APK 的对话框
    """
    def __init__(self, window=None):
        super().__init__("提取应用")
        self.mainWindow = window
        self.app_list = []  # 存储应用信息 (包名, APK路径)
        self.initWindow()
        # 加载应用列表
        self._load_app_list()

    def initWindow(self):
        super().initWindow()
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setFixedSize(700, 500)

        self.root_layout = QtWidgets.QVBoxLayout(self)
        self.root_layout.setObjectName("root_layout")

        # 搜索区域
        self.search_layout = QtWidgets.QHBoxLayout()
        self.search_layout.setObjectName("search_layout")

        self.search_label = QtWidgets.QLabel(self)
        self.search_label.setObjectName("search_label")
        self.search_label.setText("搜索:")

        self.search_edit = QtWidgets.QLineEdit(self)
        self.search_edit.setObjectName("search_edit")
        self.search_edit.setPlaceholderText("输入包名或应用名进行筛选...")
        self.search_edit.textChanged.connect(self._on_search_text_changed)

        # 应用类型筛选下拉框
        self.filter_combo = QtWidgets.QComboBox(self)
        self.filter_combo.setObjectName("filter_combo")
        self.filter_combo.addItem("全部应用")
        self.filter_combo.addItem("第三方应用")
        self.filter_combo.addItem("系统应用")
        self.filter_combo.currentIndexChanged.connect(self._on_filter_changed)

        self.refresh_btn = QtWidgets.QPushButton(self)
        self.refresh_btn.setObjectName("refresh_btn")
        self.refresh_btn.setText("刷新")
        self.refresh_btn.clicked.connect(self._load_app_list)

        self.search_layout.addWidget(self.search_label)
        self.search_layout.addWidget(self.search_edit)
        self.search_layout.addWidget(self.filter_combo)
        self.search_layout.addWidget(self.refresh_btn)
        self.search_layout.setStretch(0, 0)
        self.search_layout.setStretch(1, 1)
        self.search_layout.setStretch(2, 0)
        self.search_layout.setStretch(3, 0)

        # 应用列表表格
        self.app_table = QtWidgets.QTableWidget(self)
        self.app_table.setObjectName("app_table")
        self.app_table.setColumnCount(3)
        self.app_table.setHorizontalHeaderLabels(["包名", "APK路径", "类型"])
        self.app_table.setSelectionBehavior(QtWidgets.QAbstractItemView.SelectRows)
        self.app_table.verticalHeader().setVisible(True)  # 显示垂直表头（行号）
        self.app_table.setSelectionMode(QtWidgets.QAbstractItemView.SingleSelection)
        # 禁用自动换行(解决在Stretch 模式下，Qt为"可能的换行"预留额外空间，导致省略号现在在距离右侧的一定距离的问题)
        self.app_table.setWordWrap(False)
        # 设置列宽: 包名(固定) : APK路径(拉伸) : 类型(固定)
        # 包名列保持固定宽度 180px
        self.app_table.horizontalHeader().setSectionResizeMode(0, QtWidgets.QHeaderView.Interactive)
        self.app_table.setColumnWidth(0, 180)
        # APK路径列自动拉伸填充
        self.app_table.horizontalHeader().setSectionResizeMode(1, QtWidgets.QHeaderView.Stretch)
        # 类型列固定宽度 70px
        self.app_table.horizontalHeader().setSectionResizeMode(2, QtWidgets.QHeaderView.Interactive)
        self.app_table.setColumnWidth(2, 70)
        self.app_table.setEditTriggers(QtWidgets.QAbstractItemView.NoEditTriggers)

        # 按钮区域
        self.button_layout = QtWidgets.QHBoxLayout()
        self.button_layout.setObjectName("button_layout")

        self.pull_btn = QtWidgets.QPushButton(self)
        self.pull_btn.setObjectName("pull_btn")
        self.pull_btn.setText("提取选中应用")
        self.pull_btn.setEnabled(False)
        self.pull_btn.clicked.connect(self._on_pull_clicked)

        self.close_btn = QtWidgets.QPushButton(self)
        self.close_btn.setObjectName("close_btn")
        self.close_btn.setText("关闭")
        self.close_btn.clicked.connect(self.close)

        self.button_layout.addStretch()
        self.button_layout.addWidget(self.pull_btn)
        self.button_layout.addWidget(self.close_btn)

        # 添加到主布局
        self.root_layout.addLayout(self.search_layout)
        self.root_layout.addWidget(self.app_table)
        self.root_layout.addLayout(self.button_layout)

        # 表格选择变化监听
        self.app_table.itemSelectionChanged.connect(self._on_selection_changed)

    def _on_selection_changed(self):
        """当表格选择变化时"""
        has_selection = len(self.app_table.selectedItems()) > 0
        self.pull_btn.setEnabled(has_selection)

    def _on_search_text_changed(self, text):
        """搜索框文本变化时过滤列表"""
        self._apply_filters()

    def _on_filter_changed(self, index):
        """筛选下拉框变化时过滤列表"""
        self._apply_filters()

    def _apply_filters(self):
        """应用所有筛选条件（搜索框 + 类型筛选）"""
        filter_text = self.search_edit.text().lower()
        filter_type = self.filter_combo.currentIndex()  # 0=全部, 1=第三方, 2=系统

        visible_index = 1  # 可见行的索引计数器
        for row in range(self.app_table.rowCount()):
            package_item = self.app_table.item(row, 0)
            type_item = self.app_table.item(row, 2)

            if package_item and type_item:
                package_name = package_item.text().lower()
                app_type = type_item.text()
                # 检查搜索文本匹配
                text_match = filter_text in package_name
                # 检查类型匹配
                type_match = False
                if filter_type == 0:  # 全部
                    type_match = True
                elif filter_type == 1 and app_type == "第三方":  # 第三方
                    type_match = True
                elif filter_type == 2 and app_type == "系统":  # 系统
                    type_match = True

                # 同时满足文本和类型条件才显示
                should_show = text_match and type_match
                self.app_table.setRowHidden(row, not should_show)

                # 更新垂直表头标签
                if should_show:
                    self.app_table.setVerticalHeaderItem(row, QtWidgets.QTableWidgetItem(str(visible_index)))
                    visible_index += 1
                else:
                    self.app_table.setVerticalHeaderItem(row, QtWidgets.QTableWidgetItem(""))

    def _load_app_list(self):
        """加载设备已安装的所有应用列表（包括系统和第三方）"""
        if not self.mainWindow.has_any_connected_devices():
            z_logger.error("没有已连接的设备")
            return

        self.app_table.setRowCount(0)
        self.app_list.clear()
        self.search_edit.clear()

        device_ip = self.mainWindow.current_device_addr
        z_logger.info("开始获取应用列表：")

        # 使用 ADBTools 的异步方法获取应用列表
        self.mainWindow.adbTools.get_installed_apps(
            device_ip,
            on_loaded_callback=self._on_app_list_loaded,
            on_failed_callback=self._on_app_list_load_failed
        )

    def _on_app_list_loaded(self, app_list):
        """应用列表加载完成回调"""
        self.app_list = app_list
        self._populate_table()

    def _on_app_list_load_failed(self, error_msg):
        """应用列表加载失败回调"""
        z_logger.error(f"获取应用列表失败: {error_msg}")

    def _populate_table(self):
        """填充应用列表表格"""
        self.app_table.setRowCount(0)
        for package, apk_path, app_type in self.app_list:
            row = self.app_table.rowCount()
            self.app_table.insertRow(row)

            # 包名
            package_item = QtWidgets.QTableWidgetItem(package)
            package_item.setToolTip(package)  # 设置悬浮提示显示完整包名
            package_item.setTextAlignment(QtCore.Qt.AlignLeft | QtCore.Qt.AlignVCenter)
            self.app_table.setItem(row, 0, package_item)

            # APK 路径
            path_item = QtWidgets.QTableWidgetItem(apk_path)
            path_item.setToolTip(apk_path)  # 设置悬浮提示显示完整路径
            path_item.setTextAlignment(QtCore.Qt.AlignLeft | QtCore.Qt.AlignVCenter)
            self.app_table.setItem(row, 1, path_item)

            # 类型
            type_item = QtWidgets.QTableWidgetItem(app_type)
            type_item.setTextAlignment(QtCore.Qt.AlignCenter | QtCore.Qt.AlignVCenter)
            self.app_table.setItem(row, 2, type_item)

    def _on_pull_clicked(self):
        """点击提取按钮"""
        selected_rows = self.app_table.selectionModel().selectedRows()
        if not selected_rows:
            return

        row = selected_rows[0].row()
        package = self.app_table.item(row, 0).text()
        apk_path = self.app_table.item(row, 1).text()

        # 弹出保存文件对话框
        default_filename = f"{package.replace('.', '_')}.apk"
        save_path, _ = QFileDialog.getSaveFileName(
            self,
            "保存 APK 文件",
            default_filename,
            "APK Files (*.apk);;All Files (*)"
        )

        if save_path:
            self._pull_apk(apk_path, save_path)

    def _pull_apk(self, remote_path, local_path):
        """执行 adb pull 命令提取 APK"""
        device_ip = self.mainWindow.current_device_addr
        cmd = f"adb -s {device_ip} pull \"{remote_path}\" \"{local_path}\""
        z_logger.info(f"正在提取 APK: {remote_path} -> {local_path}")
        self.mainWindow.adbTools.async_exec_adb_cmd([cmd])


class TextInputDialog(BaseDialog):
    def __init__(self,  window = None):
        super().__init__("文本输入")
        self.mainWindow = window
        self.initWindow()

    def initWindow(self):
        super().initWindow()
        # 只显示关闭按钮, 不显示最大化, 最小化, 并且固定窗口大小
        self.setWindowFlags(Qt.WindowCloseButtonHint)
        self.setObjectName("TextInputDialog")
        self.resize(500, 140)

        # 主垂直布局
        self.verticalLayout = QtWidgets.QVBoxLayout(self)
        self.verticalLayout.setObjectName("verticalLayout")
        self.verticalLayout.setSpacing(10)

        # 使用 QPlainTextEdit 替代 QLineEdit，支持多行输入
        self.text_edit = QtWidgets.QPlainTextEdit(self)
        self.text_edit.setObjectName("text_edit")
        self.text_edit.setPlaceholderText("请输入要发送到设备的文本...")
        self.text_edit.setVerticalScrollBarPolicy(QtCore.Qt.ScrollBarAsNeeded)
        self.verticalLayout.addWidget(self.text_edit)

        # 按钮区域水平布局
        button_layout = QtWidgets.QHBoxLayout()
        button_layout.setObjectName("button_layout")
        button_layout.setSpacing(10)

        # 清空按钮
        self.btn_clear = QtWidgets.QPushButton(self)
        self.btn_clear.setObjectName("btn_clear")
        self.btn_clear.clicked.connect(self._on_clear_clicked)
        button_layout.addWidget(self.btn_clear)

        # 弹性空间
        button_layout.addStretch()

        # 输入按钮
        self.btn_input_text = QtWidgets.QPushButton(self)
        self.btn_input_text.setObjectName("btn_input_text")
        self.btn_input_text.clicked.connect(self.doTextInput)
        button_layout.addWidget(self.btn_input_text)

        self.verticalLayout.addLayout(button_layout)

        self.retranslateUi()
        QtCore.QMetaObject.connectSlotsByName(self)

    def retranslateUi(self):
        _translate = QtCore.QCoreApplication.translate
        self.btn_input_text.setText(_translate("TextInputDialog", "输入"))
        self.btn_clear.setText(_translate("TextInputDialog", "清空"))

    def _on_clear_clicked(self):
        """清空输入框内容"""
        self.text_edit.clear()

    def doTextInput(self):
        """执行文本输入到设备"""
        _text = self.text_edit.toPlainText()
        if _text:
            # 处理换行符，将换行转换为空格
            _text = _text.replace('\n', ' ')
            # 处理空格（ADB input text 中空格需要用 %s 替代）
            _text = _text.replace(' ', '%s')
            self.mainWindow.adbTools.exec_adb_cmd(f"adb -s {self.mainWindow.current_device_addr} shell input text {_text}")
            # 执行成功后清空输入框
            self.text_edit.clear()


class APKHelperDialog(DragDialog):
    # 解析完毕的信号
    parseFinished = pyqtSignal(dict)

    config_manager = AppConfigManager(COMMON_CONFIG_FILE_PATH)

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
        self.apkInfoGroupBox.setFont(getSimpleFontStyle(size=UiUtils.getScaleValue(10)))
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
            lineEdit.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(9)))
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
                lineEdit.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(9)))
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
        self.fileInfoGroupBox.setFont(getSimpleFontStyle(size=UiUtils.getScaleValue(10)))
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
            lineEdit.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(9)))
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
        labelView.setFont(getWRYHFontStyle(size=UiUtils.getScaleValue(10)))
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
    dialog = AboutDialog()
    # 设置窗口的属性为ApplicationModal模态，用户只有关闭弹窗后，才能关闭主界面
    dialog.setWindowModality(Qt.ApplicationModal)
    dialog.show()
    sys.exit(app.exec_())



