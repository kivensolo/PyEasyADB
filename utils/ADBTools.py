from logcat.log import z_logger
from utils.CmdExecutor import CmdExecutor


class ADBTools:

    def __init__(self):
        super(ADBTools, self).__init__()
        # 不能把executor放入_exec_cmd中，出栈的时候会被回收
        self.executor = CmdExecutor()
        self.current_cmd = ''

    def isGettingDeviceList(self):
        return self.current_cmd == 'adb devices'

    def _exec_cmd(self, cmd, block):
        """
        执行ADB命令
        :param cmd: ADB执行命令
        :param block:  回调函数
        :return:  list
        """
        z_logger.info("[CMD]: " + cmd)
        self.current_cmd = cmd
        self.executor.setFinishCallback(block)
        self.executor.exec(cmd)

    def exec_cmd(self, ip, cmd="", isShell=False, block=None):
        if isShell:
            adb_cmd = "adb -s {0} shell {1}".format(ip, cmd)
        else:
            adb_cmd = "adb -s {0} {1}".format(ip, cmd)
        self._exec_cmd(adb_cmd, block)

    def start_app_page(self, ip, class_path, block):
        """
        根据class路径启动目标应用页面
        :param class_path:
        :param block:
        :return:
        """
        adb_cmd = "adb -s {0} shell am start {1}".format(ip, class_path)
        z_logger.info("Start app: %s" % class_path)
        self._exec_cmd(adb_cmd, block)

    def get_devices_state(self, block):
        cmd = 'adb devices'
        self._exec_cmd(cmd, block)

    def connect_device(self, device_ip, block):
        cmd = "adb connect %s" % device_ip
        self._exec_cmd(cmd, block)

    def disconnect_device(self, device_ip, block):
        cmd = "adb disconnect %s" % device_ip
        self._exec_cmd(cmd, block)

    # TODO 优化，统一记录ip
    def get_device_info(self, ip, block):
        cmd = "adb -s {0} shell getprop".format(ip)
        self._exec_cmd(cmd, block)
