import os
import signal
import subprocess

from PyQt5.QtCore import QThread, pyqtSignal

from src.logcat.log import z_logger
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
            _full_cmd = f"adb -s {self.target_device_ip} shell {_cmd}"
        else:
            _full_cmd = f"adb -s {self.target_device_ip} {_cmd}"
        return _full_cmd


def get_filter_processes(device_name):
    """
    获取进程信息
    ['USER', 'PID', 'PPID', 'VSZ', 'RSS', 'WCHAN', 'ADDR', 'S', 'NAME']
    ['USER', 'PID', 'PPID', 'VSIZE', 'RSS', 'WCHAN', 'PC', 'NAME']
    :return:
    """
    processes = []
    try:
        _cmd = f"adb -s {device_name} shell ps"
        output = subprocess.check_output(_cmd, shell=True).decode("utf-8")
    except Exception as e:
        z_logger.error("获取进程信息失败:" + e)
        return []

    # 从第二行开始 分割每一行
    for line in output.splitlines()[1:]:
        if len(line) == 0:  # blank content
            continue
        z_logger.debug("process line info:\n" + line)
        columns = line.split()
        if len(columns) == 0:
            z_logger.error("process line info error.")
            continue
        user = columns[0]
        if process_user_name_check(user):
            continue
        # if columns[1] == "PID": # 可能是列表头
        #     continue

        pid = int(columns[1])  # 获取pid值
        if pid <= 1000:  # 过滤系统进程
            continue

        name = str(columns[-1])  # 获取最后一列 Name名称
        # 针对部分设备 sh、ping是用户目录的情况做过滤
        filterName = ["sh", "ping"]
        for _name in filterName:
            if name == _name:
                continue

        # 使用列表解析+any()函数, 过滤[aml_pwrsave_wq]、系统应用等无需展示的进程
        filterPrefixes = ["[", "android.", "/system", "com.android", "sysyem_server", "libcpu", "/data/"]
        if any(name.startswith(prefix) for prefix in filterPrefixes):
            continue
        processes.append((user, pid, name))
    return processes


def process_user_name_check(user):
    # 如果不是用户\蓝牙\系统进程,则过滤掉，比如root、wifi、shell、dhcp等用户
    return not str(user).startswith("u0_") \
        and user != "system" \
        and user != "bluetooth"


class AsyncAdbThread(QThread):
    output_received = pyqtSignal(str)
    name = "Async_adb_thread"

    def __init__(self):
        super().__init__()

        self.cmds = ""
        self.process = None
        self.isStoped = False

    def run(self):
        self.isStoped = False
        print("ADB子线程运行")
        for _cmd in self.cmds:
            if self.isStoped:
                # 手动终止，不执行任何命令
                break
            if isinstance(_cmd,ActionCmdParams):
                _cmd = _cmd.getAdbCMD()
            self.output_received.emit(_cmd)
            self.process = subprocess.Popen(
                _cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8'
            )
            while True:
                stdout = self.process.stdout.readline()
                if stdout:
                    self.output_received.emit(stdout)
                else:
                    break   # 输出结束，中断循环并退出线程

            # 会阻塞，所以没法和stdout放在一些读取
            stderr = self.process.stderr.read()
            if stderr:
                self.output_received.emit(stderr)
            # process.communicate()  # 等待命令完成
        self.exit()  # 返回状态(不是严格必要的)

    def stop(self):
        self.output_received.emit("录屏已终止")
        self.isStoped = True
        os.kill(self.process.pid, signal.SIGINT)


class ADBTools:

    def __init__(self):
        super(ADBTools, self).__init__()
        # 不能把executor放入exec_adb_cmd中，出栈的时候会被回收
        self.thread = AsyncAdbThread()
        self.thread.output_received.connect(self.on_screen_record_emit_sigle)
        self.executor = CmdExecutor()
        self.current_cmd = ''

    def isGettingDeviceList(self):
        return self.current_cmd == 'adb devices'

    def exec_adb_cmd(self, cmd, block=None):
        """
        执行ADB命令
        :param cmd: ADB执行命令
        :param block:  回调函数
        :return:  list
        """
        z_logger.info(cmd)
        self.current_cmd = cmd
        self.executor.setFinishCallback(block)
        self.executor.exec(cmd)

    def async_exec_adb_cmd(self, cmds):
        """
        异步执行adb命令，支持多批次命令
        :param cmds:
        :return:
        """
        try:
            self.thread.cmds = cmds
            self.thread.start()
        except Exception as e:
            print(e)

    @DeprecationWarning
    def _exec_cmd(self, ip, cmd="", is_shell=False, block=None):
        if is_shell:
            adb_cmd = "adb -s {0} shell {1}".format(ip, cmd)
        else:
            adb_cmd = "adb -s {0} {1}".format(ip, cmd)
        self.exec_adb_cmd(adb_cmd, block)

    def start_app_page(self, ip, class_path, block):
        """
        根据class路径启动目标应用页面
        :param class_path:
        :param block:
        :return:
        """
        adb_cmd = "adb -s {0} shell am start {1}".format(ip, class_path)
        z_logger.info("Start app: %s" % class_path)
        self.exec_adb_cmd(adb_cmd, block)

    def get_devices_state(self, block):
        cmd = 'adb devices'
        self.exec_adb_cmd(cmd, block)

    def connect_device(self, device_ip, block):
        cmd = "adb connect %s" % device_ip
        self.exec_adb_cmd(cmd, block)

    def disconnect_device(self, device_ip, block):
        cmd = "adb disconnect %s" % device_ip
        self.exec_adb_cmd(cmd, block)

    # TODO 优化，统一记录ip
    def get_device_info(self, ip, block):
        cmd = "adb -s {0} shell getprop".format(ip)
        self.exec_adb_cmd(cmd, block)

    # FIXME 卡主线程的
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

    def get_running_process(self, device_name, blcok):
        filtered_processes = get_filter_processes(device_name)
        # 按照A-Z顺序对进程名称进行排序
        sorted_processes = sorted(filtered_processes, key=lambda x: x[2])
        blcok(sorted_processes)
        # for user, pid, p_name in sorted_processes:
        #     print(f"Process Name: {p_name}, PID: {pid}")

    def start_screen_record(self, device_ip, record_cmd, tmp_path, pull_path):
        """
        获取屏幕录制数据
        :param device_ip:   设备ip端口地址
        :param record_cmd:  录制的命令
        :param tmp_path:    设备的临时目录
        :param pull_path:   电脑本机储存目录
        :return:
        """
        cmd_1 = f'adb -s {device_ip} exec-out {record_cmd}'
        cmd_2 = f"adb -s {device_ip} shell sleep 5"  # 等待5s,等数据写入mp4文件
        cmd_3 = f"adb pull {tmp_path} {pull_path}"
        cmd_4 = f"adb shell rm {tmp_path}"
        cmds = [cmd_1, cmd_2, cmd_3, cmd_4]
        z_logger.info("Start screen record.")
        self.async_exec_adb_cmd(cmds)

    def stop_screen_record(self):
        if self.thread.isRunning():
            self.thread.stop()

    def on_screen_record_emit_sigle(self, data):
        # 当屏幕录制中发送信号
        z_logger.info(data)
