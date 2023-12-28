import sys

# 基本控件位于pyqt5.qtwidgets模块中
from PyQt5.QtWidgets import QApplication
from ui.MainWindow import MainWindow


class App:
    def __init__(self):
        super.__init__()


if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = MainWindow()
    # 使程序进入主循环(应用程序的消息循环队列),主循环会获取并分发事件。
    sys.exit(app.exec_())
