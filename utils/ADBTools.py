import re
import subprocess

from logcat.log import z_logger
from utils.CmdExecutor import CmdExecutor


class ActionCmdParams:
    def __init__(self):
        self.isShellMode = True
        # 行为命令id, 包含自定义行为，也可能直接是ADB行为命令
        self.action = ""
        self.target_device_ip = ""
        self.needDstPkg = True
        self.target_app = ""

    # def __init__(self, is_shell, format_cmd: string, ip="", app=""):
    #     self.isShellMode = is_shell
    #     self.cmd_with_format = format_cmd
    #     self.target_device_ip = ip
    #     self.target_app = app

    def getAdbCMD(self):
        _cmd = self.action.format(self.target_app)
        _full_cmd = ''
        if self.isShellMode:
            _full_cmd = "adb -s {0} shell {1}".format(self.target_device_ip, _cmd)
        else:
            _full_cmd = "adb -s {0} {1}".format(self.target_device_ip, _cmd)
        return _full_cmd


def get_filter_processes():
    """
    获取进程信息
    ['USER', 'PID', 'PPID', 'VSZ', 'RSS', 'WCHAN', 'ADDR', 'S', 'NAME']
    :return:
    """
    output = subprocess.check_output("adb shell ps", shell=True).decode("utf-8")
    processes = []
    for line in output.splitlines()[1:]:
        columns = line.split()
        user = columns[0]
        if not str(user).startswith("u0_") \
                and user != "system" and user != "bluetooth":
            # 如果不是用户\蓝牙\系统进程,则过滤掉，比如root、wifi、shell等用户
            continue
        # if columns[1] == "PID": # 可能是列表头
        #     continue
        pid = int(columns[1])   # 获取pid值
        if pid <= 1000:
            # 过滤系统进程
            continue
        name = columns[-1]      # 获取最后一列 Name名称
        if str(name).startswith("["):
            # 过滤[aml_pwrsave_wq] 这种进程
            continue
        processes.append((user, pid, name))
    return processes


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

    def exec_adb_cmd(self, cmd, block):
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

    def exec_cmd(self, ip, cmd="", is_shell=False, block=None):
        if is_shell:
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

    def get_screen_shoot(self, device_ip, save_path):
        # 执行adb exec-out screencap命令，并将输出重定向到文件
        cmd = 'adb -s {0} exec-out screencap -p > {1}'.format(device_ip, save_path)
        process = subprocess.Popen(cmd, shell=True, stdout=subprocess.PIPE)
        process.communicate()
        return_code = process.returncode
        if return_code == 0:
            z_logger.info(f"Screenshot captured successfully! > {save_path}")
        else:
            z_logger.error("Failed to capture screenshot.")

    def get_running_process(self):
        filtered_processes = get_filter_processes()
        # 按照A-Z顺序对进程名称进行排序
        sorted_processes = sorted(filtered_processes, key=lambda x: x[2])
        for user, pid, p_name in sorted_processes:
            print(f"Process Name: {p_name}, PID: {pid}")
