import subprocess

from logcat import log
from utils import Tools


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
    log.d("cmd ---> " + cmd)
    status, result = Tools.exec_cmd(cmd)
    if status:
        if act == 'connect':
            self.changeConnectBtnState(False, True)
            deviceInfo = Tools.getDeviceInfo()
        else:
            self.changeConnectBtnState(True, False)

def getDeviceInfo():
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
    sdk = _process.stdout.read().strip()    # api版本
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
