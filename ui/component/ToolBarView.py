import sys

from PyQt5.QtCore import QSize, pyqtSlot, Qt
from PyQt5.QtWidgets import QToolBar, QHBoxLayout, QLabel, QComboBox, QApplication

from logcat.log import z_logger
from ui.widget.Dialogs import AddPackageDialog, NewConnectDialog
from utils.UITools import IconTool
from utils.UiWidgts import AppPushButton


class AppToolBar(QToolBar):
    selected_pkg = ''
    """
    App快捷工具栏(菜单栏下面)
    """
    def __init__(self, context):
        super().__init__()
        self.mainWindow = context
        self.root_layout = QHBoxLayout()
        self.setLayout(self.root_layout)

        self.setContentsMargins(5, 5, 5, 5)
        self.setStyleSheet("QWidget{background-color:rgb(229,229,229);border:none}")
        self.initNewConnectBtn()
        self.initAddNewPackageBtn()

    def initNewConnectBtn(self):
        icon = IconTool.buildQIcon("add_new.png", "icons")
        tool_item_add_new = AppPushButton("", self.show_new_device_dialog)
        tool_item_add_new.setIcon(icon)
        tool_item_add_new.setIconSize(QSize(30, 30))
        tool_item_add_new.setToolTip("Add connect")
        # tool_item_add_new.setFlat(True)  # 按钮扁平化,去掉按钮边框
        self.addWidget(tool_item_add_new)

    def initAddNewPackageBtn(self):
        icon = IconTool.buildQIcon("new_connect.png")
        tool_item_add_new = AppPushButton("", self.show_add_package_dialog)
        tool_item_add_new.setIcon(icon)
        tool_item_add_new.setIconSize(QSize(30, 30))
        tool_item_add_new.setToolTip("Add package")
        # tool_item_add_new.setFlat(True)  # 按钮扁平化,去掉按钮边框
        self.addWidget(tool_item_add_new)

    @pyqtSlot()
    def show_new_device_dialog(self):
        new_connect_dialog = NewConnectDialog(self.mainWindow, self.mainWindow.add_device)
        new_connect_dialog.setWindowModality(Qt.ApplicationModal)
        new_connect_dialog.exec()

    @pyqtSlot()
    def show_add_package_dialog(self):
        dialog = AddPackageDialog(self.mainWindow, self.mainWindow.add_new_package)
        dialog.setWindowModality(Qt.ApplicationModal)
        dialog.exec()

    def initPkgData(self):
        """
        从数据库初始化包名数据信息
        :return:
        """
        if not self.dbManager:
            z_logger.error('数据库连接异常，请重启应用.')
            return
        self.updatePkgComBox()

if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = AppToolBar(None)
    mainWin.show()
    sys.exit(app.exec_())