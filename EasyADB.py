import sys
import traceback

from PyQt5.QtWidgets import QApplication

from Dependencies import Dependencies
from src.MainWindow import MainWindow
from src.logcat.log import z_logger


def exception_handler(exc_type, exc_value, exc_traceback):
    # App的自定义异常处理函数
    error_msg = ''.join(traceback.format_exception(exc_type, exc_value, exc_traceback))
    z_logger.error(error_msg)


sys.excepthook = exception_handler


class App:
    def __init__(self):
        super.__init__()


if __name__ == '__main__':
    app = QApplication(sys.argv)
    try:
        Dependencies().Check()
    except Exception as e:
        print(e)
        sys.exit(0)
    ex = MainWindow()
    # 使程序进入主循环(应用程序的消息循环队列),主循环会获取并分发事件。
    sys.exit(app.exec_())
