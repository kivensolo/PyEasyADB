import sys

from PyQt5.QtCore import Qt, pyqtSlot
from PyQt5.QtWidgets import QLineEdit, QApplication, QLabel, QPushButton

from ui.widget.BaseDialog import BaseDialog
from utils import Tools
from utils.UITools import IconTool


class NewConnectDialog(BaseDialog):
    """
    新建连接的Dialog, 使用绝对布局
    """
    def __init__(self, window, block):
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
        state, msg = self.window.dbManager.add_device_to_db(new_ip)
        if state:
            print("ip合适，已储存至数据库")
            # TODO 事件发送 传递给MainWindow，刷新treeView
            self.block(new_ip+":5555")
            self.close()
        else:
            print(msg)


if __name__ == "__main__":
    app = QApplication(sys.argv)
    dialog = NewConnectDialog()
    # 设置窗口的属性为ApplicationModal模态，用户只有关闭弹窗后，才能关闭主界面
    dialog.setWindowModality(Qt.ApplicationModal)
    dialog.show()
    sys.exit(app.exec_())



