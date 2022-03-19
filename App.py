import sys

# 基本控件位于pyqt5.qtwidgets模块中
from PyQt5.QtWidgets import QApplication
from ui.MainWindow import MainWindow


class App:
    def __init__(self):
        super.__init__()


if __name__ == '__main__':

    # 每一pyqt5应用程序必须创建一个应用程序对象。
    # sys.argv参数是一个列表，从命令行输入参数。
    app = QApplication(sys.argv)
    ex = MainWindow()
    # 使程序进入主循环(应用程序的消息循环队列),主循环会获取并分发事件。
    sys.exit(app.exec_())
