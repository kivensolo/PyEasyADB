import logging
import os
import re
import signal
import subprocess
import time

from PyQt5.QtCore import QThread, pyqtSignal, pyqtSlot

from src.logcat.log import z_logger
from utils.CmdExecutor import CmdExecutor
from utils.Utils import LogUtils, _simpleNameToLevel, _nameToLevel


class ActionCmdParams:
    def __init__(self, isShell=True, needPackage=True):
        self.isShellMode = isShell
        self.needDstPkg = needPackage
        # ADB行为命令
        self.cmd = ""
        # 自定义行为
        self.custom_action = ""
        # 目标设备ip
        self.target_device_ip = ""
        self.target_app = ""

    def hasCustomAction(self):
        return self.custom_action != ""

    def getAdbCMD(self):
        _cmd = self.cmd.format(self.target_app)
        _full_cmd = ''
        if self.isShellMode:
            _full_cmd = f"adb -s {self.target_device_ip} shell {_cmd}"
        else:
            _full_cmd = f"adb -s {self.target_device_ip} {_cmd}"
        return _full_cmd

    def verifyTargetApp(self):
        """
        校验该行为是否需要目标应用包名
        :return: 是否校验通过  True|False
        """
        if self.needDstPkg:
            if self.target_app == "":
                return False
            else:
                return True
        else: # no need check target app
            return True


def get_filter_processes(device_name):
    """
    获取进程信息
    ['USER', 'PID', 'PPID', 'VSZ', 'RSS', 'WCHAN', 'ADDR', 'S', 'NAME']
    ['USER', 'PID', 'PPID', 'VSIZE', 'RSS', 'WCHAN', 'PC', 'NAME']
    :return: state, proceList
    """
    processes = []
    try:
        _cmd = f"adb -s {device_name} shell ps"
        output = subprocess.check_output(_cmd, shell=True).decode("utf-8")
    except Exception as e:
        z_logger.error("获取进程信息失败:" + str(e))
        return False, []

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
    return True, processes


def process_user_name_check(user):
    # 如果不是用户\蓝牙\系统进程,则过滤掉，比如root、wifi、shell、dhcp等用户
    return not str(user).startswith("u0_") \
        and user != "system" \
        and user != "bluetooth"


class LiveLogAdbThread(QThread):
    live_log_dump_signal = pyqtSignal(str)
    name = "Live_log_adb_thread"

    def __init__(self):
        super().__init__()

        self.cmd = ""
        self.process = None
        self.isRunning = False
        self.logcatFilter = self.LogCatFilter()

    def run(self):
        if self.isRunning:
            # 手动终止，不执行任何命令
            return
        z_logger.debug("启动实时日志输出.....")
        self.isRunning = True
        if isinstance(self.cmd, ActionCmdParams):
            _cmd = self.cmd.getAdbCMD()
        else:
            _cmd = self.cmd
        self.process = subprocess.Popen(
            _cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8'
        )
        z_logger.debug("开启subprocess.....")
        while True:
            stdout = self.process.stdout.readline()
            if stdout:
                logMsg = stdout.rstrip("\n")
                if len(logMsg) == 0:
                    # 部分设备(例如S3、小米S4)会在每条输出后输出\n,这种数据过滤掉
                    continue

                # 记录每一条原始数据
                self.logcatFilter.record(logMsg)

                # 进行数据级别过滤
                isFiltered, pid, level = self.logcatFilter.filter(logMsg)
                if isFiltered:
                    continue
                # url高亮处理
                content = LogUtils.highlight_link_addr(logMsg)
                # 着色处理
                ui_log = LogUtils.changeLogColor(False, level, content)

                # 每条数据发送等待10ms,防止GUI频繁渲染导致的卡顿
                time.sleep(1 / 100)

                # 发送给UI线程
                self.live_log_dump_signal.emit(ui_log)
                continue
            else:
                break   # 输出结束，中断循环并退出线程

        # 会阻塞，所以没法和stdout放在一些读取
        stderr = self.process.stderr.read()
        if stderr:
            self.live_log_dump_signal.emit(stderr)
        # process.communicate()  # 等待命令完成
        self.exit()  # 返回状态(不是严格必要的)

    def stop(self):
        z_logger.debug("停止实时日志输出！")
        self.isRunning = False
        self.clearFilter()
        os.kill(self.process.pid, signal.SIGINT)

    def clearFilter(self):
        self.logcatFilter.clear()

    def getHistoryLogsWithRules(self):
        return self.logcatFilter.getHistoryLogsWithRules()

    class LogCatFilter(object):
        # 过滤的pid
        selected_pid = ""
        # 日志过滤级别(只显示 >= 此级别的日志) 默认ALL
        _filtered_level = logging.NOTSET
        _only_show_selected_app_log = False
        log_cache_list = []
        # 过滤后的gui历史log
        gui_history_log = []

        def __init__(self):
            pass

        def setOnlyShowSelectedPidLog(self, enable: bool):
            self._only_show_selected_app_log = enable

        def changeFilterLevelByName(self, levelName: str):
            """
            设置过滤级别tag
            :param levelName: I\W\E\D 等
            """
            if len(levelName) == 1:
                self._filtered_level = _simpleNameToLevel.get(levelName, logging.NOTSET)
            else:
                self._filtered_level = _nameToLevel.get(levelName, logging.NOTSET)

        def onSelectedPidChanged(self, pid=""):
            self.selected_pid = pid

        def filter(self, logMsg):
            """
            单条实时日志的过滤处理
            :param logMsg: 原始的单条日志数据
                     01-22 11:20:30.188 W/InputMethodManagerService( 1884): LogMessage
            :return:
            返回格式:
                <是否会被过滤(True|False)> <当前日志的进程pid> <当前日志的级别(数字)>
            """

            pattern  = re.compile(r'^.+\s([VIDWE])\/.+\(\s*(\d+)\)\:.+$')
            match = pattern.search(logMsg)
            if match:
                _level_name = match.group(1)
                _pid = match.group(2)
            else:
                _pid = "-1"
                _level_name = "I"

            _level = _simpleNameToLevel.get(_level_name, logging.NOTSET)

            # Level——1: 日志级别过滤
            if _level < self._filtered_level:
                return True, _pid, _level
                # TODO 如果需要过滤,则更新历史数据

            # Level——2: 进程过滤
            if self._only_show_selected_app_log:
                # 与选中进程不一致的进程需要被过滤掉
                isFilter = (_pid != self.selected_pid)
                return isFilter, _pid, _level
            else:
                return False, _pid, _level

        def record(self, _historyLog):
            """
            记录获取到的每一条日志数据
            :param _historyLog:
            :return:
            """
            # TODO 同步
            self.log_cache_list.append(_historyLog)

        def clear(self):
            self.gui_history_log.clear()
            self.log_cache_list.clear()

        def getHistoryLogsWithRules(self):
            """
            从缓存列表中，获取历史日志书
            :return: ，收集过滤后的数据
            """
            # TODO 改为子线程
            self.gui_history_log.clear()
            for _log in self.log_cache_list:
                filtered, pid, level = self.filter(_log)
                if not filtered:
                    # url高亮处理
                    content = LogUtils.highlight_link_addr(_log)
                    # 着色处理
                    ui_log = LogUtils.changeLogColor(False, level, content)
                    self.gui_history_log.append(ui_log)
                else:
                    continue

            return "\n".join(self.gui_history_log)


class AsyncAdbThread(QThread):
    output_received = pyqtSignal(list)
    name = "Async_adb_thread"

    def __init__(self):
        super().__init__()

        self.cmds = ""

        self.process = None
        self.isStoped = False

    def run(self):
        print("ADB子线程运行")
        for _cmd in self.cmds:
            if self.isStoped:
                # 手动终止，不执行任何命令
                break
            if isinstance(_cmd, ActionCmdParams):
                _cmd = _cmd.getAdbCMD()
            self.output_received.emit(["input", _cmd])
            self.process = subprocess.Popen(
                _cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, encoding='utf-8'
            )
            while True:
                stdout = self.process.stdout.readline()
                if stdout:
                    self.output_received.emit(["output", stdout])
                else:
                    break   # 输出结束，中断循环并退出线程

            # 会阻塞，所以没法和stdout放在一些读取
            stderr = self.process.stderr.read()
            if stderr:
                self.output_received.emit(["output", stderr])
            # process.communicate()  # 等待命令完成
        self.exit()  # 返回状态(不是严格必要的)

    def stop(self):
        self.output_received.emit(["output", "录屏已终止"])
        self.isStoped = True
        os.kill(self.process.pid, signal.SIGINT)


class ADBTools:

    def __init__(self):
        super(ADBTools, self).__init__()
        self.thread = AsyncAdbThread()
        self.thread.output_received.connect(self.on_async_single_recevied)
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
        z_logger.info_with_stamp(cmd)
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
        state, filtered_processes = get_filter_processes(device_name)
        if not state:
            # FIXME 有死循环
            return False  # 获取失败，多半是设备离线了
        # 按照A-Z顺序对进程名称进行排序
        sorted_processes = sorted(filtered_processes, key=lambda x: x[2])
        blcok(sorted_processes)
        return True
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
        z_logger.info_with_stamp("Start screen record.")
        self.async_exec_adb_cmd(cmds)

    def stop_screen_record(self):
        if self.thread.isRunning():
            self.thread.stop()

    def on_async_single_recevied(self, content):
        """
        异步执行cmd命令的输出回调
        :param content:
        :return:
        """
        if content[0] == "output":
            z_logger.info(content[1])
        else:
            z_logger.info_with_stamp(content[1])
