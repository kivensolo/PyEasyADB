import sys

from PyQt5.QtCore import Qt, pyqtSlot
from PyQt5.QtWidgets import QLineEdit, QApplication, QLabel, QPushButton, QHBoxLayout, QFileDialog
from qtpy import QtWidgets, QtCore

from src.logcat.log import z_logger
from src.DataBase import DBManager
from src.widget.BaseDialog import BaseDialog
from src.widget.CustomWidgets import DraggableLineEdit
from utils import Tools
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
        _cmd = "adb install "
        # 进行APK文件安装
        apkPath = self.file_path_edit_text.text()
        if self.rb_mode_replace.isChecked():  # -r
            _cmd += "-r "
        if self.rb_test_app.isChecked():      # -t
            _cmd += "-t "
        if self.rb_downgrade.isChecked():    # -d
            _cmd += "-d "
        _cmd += apkPath
        self.mainWindow.adbTools.exec_adb_cmd(_cmd, lambda : self.close())


if __name__ == "__main__":
    app = QApplication(sys.argv)
    dialog = installApkDialog()
    # 设置窗口的属性为ApplicationModal模态，用户只有关闭弹窗后，才能关闭主界面
    dialog.setWindowModality(Qt.ApplicationModal)
    dialog.show()
    sys.exit(app.exec_())



