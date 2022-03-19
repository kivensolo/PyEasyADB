import sys

from PyQt5.QtWidgets import QLineEdit, QApplication

from ui.widget.BaseDialog import BaseDialog
from PyQt5 import QtCore

from utils.Utils import Utils


class NewConnectDialog(BaseDialog):
    def __init__(self):
        # super(NewConnectDialog, self).__init__()
        super().__init__("新建连接")

    def initWindow(self):
        super().initWindow()
        self.setFixedSize(100, 100)
        self.setWindowModality(QtCore.Qt.ApplicationModal)

        self.inputEdit = QLineEdit(self)
        self.requestLineEdit.move(36, 15)
        self.requestLineEdit.resize(300, Utils.getItemHeight())


if __name__ == "__main__":
    app = QApplication(sys.argv)
    dialog = NewConnectDialog()
    dialog.show()
    sys.exit(app.exec_())



