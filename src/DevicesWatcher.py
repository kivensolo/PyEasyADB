import sys
import threading
import time
from subprocess import Popen, PIPE

from PyQt5.QtCore import pyqtSignal, QObject
from PyQt5.QtWidgets import QApplication, QMainWindow

from src.logcat.log import z_logger


class DeviceInfo(object):
    """
    设备名称
    可能是ip地址，也可能是纯数字、也可能是字符串
    """
    name = ""   # ip:port、名称、数字字符串等

    """
    device , 设备连接正常
    offline , 设备离线，连接出现异常
    unauthorized 设备为进行授权，需要在设备上是否允许调试对话框进行授权
    """
    state = ""

    def isConnected(self):
        """
        设备是否已连接
        :return:
        """
        return self.state == 'device' or self.state == 'offline'

    def is_device_state_normal(self):
        device_state = (self.state == 'device')
        if device_state:
            msg = "正常"
        else:
            msg = "异常"

        return device_state, msg


class DevicesWatcher(QObject):
    refreshInterval = 0
    changedSignal = pyqtSignal(list)

    def __init__(self):
        super().__init__()
        self.thread = threading.Thread(target=self.run)
        self.thread.name = 'DeviceWatcher'
        # 设置为守护线程，主线程结束时会终止子线程
        self.thread.daemon = True
        self.oldValue = ""

    def run(self):
        while True:
            # 进行ADB执行
            process = Popen('adb devices', stdout=PIPE, stderr=PIPE,
                            bufsize=-1, encoding='utf-8')
            stdout_data, stderr_data = process.communicate(input=None, timeout=20)
            if stdout_data:
                result = stdout_data.strip()
            elif stderr_data:
                result = stderr_data.strip()
            else:
                result = ""

            process.stdout.close()
            # print(result)

            if self.oldValue != result:
                self.oldValue = result

                devices_list = []
                result_split = result.split("\n")
                for line in result_split:
                    if line.startswith("List"):
                        continue

                    device_line = line.split("\t")
                    if len(device_line) < 2:
                        z_logger.debug(f"[DeviceWatcher] Unsupported format found:{line}")
                        continue

                    info = DeviceInfo()
                    info.name = device_line[0]
                    info.state = device_line[1]
                    devices_list.append(info)

                # 确保在主线程中调用观察者方法
                self.changedSignal.emit(devices_list)

            time.sleep(self.refreshInterval)

    def start(self, interval=2):
        self.refreshInterval = interval
        self.thread.start()
        z_logger.debug(f'[DeviceWatcher] thread[{self.thread.name}] started')

    def onDeviceDeleted(self):
        """
        任意设备被删除时被调用
        清除旧值，强制触发一次改变信号。
        :return:
        """
        self.oldValue = ''

class ObserverTest(QObject):
    def __init__(self, video):
        super().__init__()
        self.video = video
        self.video.changedSignal.connect(self.onChanged)

    def onChanged(self, data):
        print("设备发生变化:", data)


if __name__ == '__main__':
    app = QApplication(sys.argv)
    mainWin = QMainWindow()
    mainWin.setWindowTitle("Video Observer Example")
    mainWin.setGeometry(300, 300, 400, 200)

    watcher = DevicesWatcher()
    observer = ObserverTest(watcher)
    watcher.start()

    mainWin.show()
    sys.exit(app.exec_())
