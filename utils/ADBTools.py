import subprocess

from logcat.log import z_logger
from utils import Tools
from utils.CmdExecutor import CmdExecutor


class ADBTools():

    def __init__(self):
        super(ADBTools, self).__init__()
        # 不能把executor放入_exec_cmd中，出栈的时候会被回收
        self.executor = CmdExecutor()
        self.current_cmd = ''

    def DoIpAction(self, act):
        """
        进行设备进行连接或断开操作
        :param act:  adb的 action  connect / disconnect
        :return:
        """
        if not self.selected_ip:
            print("数据异常，无法连接")
            return
        cmd = "adb {0} {1}".format(act, self.selected_ip)
        z_logger.debug("cmd ---> " + cmd)
        status, result = Tools.exec_cmd(cmd)
        if status:
            if act == 'connect':
                self.changeConnectBtnState(False, True)
                deviceInfo = Tools.getDeviceInfo()
            else:
                self.changeConnectBtnState(True, False)

    def _exec_cmd(self, cmd, block):
        z_logger.debug("do_adb_cmd: " + cmd)
        self.current_cmd = cmd
        self.executor.setFinishCallback(block)
        self.executor.exec(cmd)

    def start_app_page(self, class_path, block):
        """
        根据class路径启动目标应用页面
        :param class_path:
        :param block:
        :return:
        """
        adb_cmd = "adb shell am start -n {0}".format(class_path)
        self._exec_cmd(adb_cmd, block)

    def get_devices_state(self, block):
        cmd = 'adb devices'
        self._exec_cmd(cmd, block)

    def connect_device(self, device_ip, block):
        cmd = "adb connect %s" % device_ip
        self._exec_cmd(cmd, block)

    def getDeviceInfo(self):
        # model = 'UnKnow'
        # version = '0.0'
        # sdk = 'UnKnow'

        _process = subprocess.Popen('adb shell getprop android.os.Build.MANUFACTURER',
                                    shell=True,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE,
                                    encoding='utf-8')
        manufacturer = _process.stdout.read().strip()  # 制造商
        _process.kill()

        _process = subprocess.Popen('adb shell getprop ro.product.model',
                                    shell=True,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE,
                                    encoding='utf-8')
        model = _process.stdout.read().strip()  # 设备型号
        _process.kill()

        _process = subprocess.Popen('adb shell getprop ro.build.version.release',
                                    shell=True,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE,
                                    encoding='utf-8')
        version = _process.stdout.read().strip()  # 系统版本号
        _process.kill()

        _process = subprocess.Popen('adb shell getprop ro.build.version.sdk',
                                    shell=True,
                                    stdout=subprocess.PIPE,
                                    stderr=subprocess.PIPE,
                                    encoding='utf-8')
        sdk = _process.stdout.read().strip()  # api版本
        _process.kill()

        return "{0} {1} Android {2} API {3}".format(manufacturer, model, version, sdk)

        # status, _manufacturer = exec_cmd('adb shell getprop android.os.Build.MANUFACTURER')
        # if status:
        #     manufacturer = _manufacturer.strip()  # 制造商
        # else:
        #     return ''
        #
        # status, _model = exec_cmd('adb shell getprop ro.product.model')
        # if status:
        #     model = _model.strip()  # 设备型号
        #
        # status, _version = exec_cmd('adb shell getprop ro.build.version.release')
        # if status:
        #     version = _version.strip()  # 系统版本号
        #
        # status, _sdk = exec_cmd('adb shell getprop ro.build.version.sdk')
        # if status:
        #     sdk = _sdk.strip()  # api版本

        # return "{0} {1} Android {2} API {3}".format(manufacturer, model, version, sdk)
