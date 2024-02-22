import os
import sys
import traceback

from PyQt5.QtWidgets import QApplication, QDesktopWidget

from Dependencies import AndroidDependencies
from src.MainWindow import MainWindow
from src.logcat.log import z_logger
from utils.Utils import Utils


def exception_handler(exc_type, exc_value, exc_traceback):
    # App的自定义异常处理函数
    error_msg = ''.join(traceback.format_exception(exc_type, exc_value, exc_traceback))
    z_logger.error(error_msg)


sys.excepthook = exception_handler

# requests库需要的CA证书设置
os.environ['REQUESTS_CA_BUNDLE'] = os.path.join(os.path.dirname(sys.argv[0]), os.path.join('certifi', 'cacert.pem'))


class App:
    def __init__(self):
        super.__init__()


if __name__ == '__main__':
    app = QApplication(sys.argv)

    # 获取当前屏幕信息
    desktop = QDesktopWidget()
    screen = desktop.screenGeometry()
    Utils.init(screen)

    try:
        AndroidDependencies().Check()
    except Exception as e:
        print(e)
        sys.exit(0)

    ex = MainWindow()
    # 使程序进入主循环(应用程序的消息循环队列),主循环会获取并分发事件。
    sys.exit(app.exec_())
