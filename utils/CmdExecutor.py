#!/usr/bin/python
# -*- coding: utf-8 -*-

from PyQt5.QtCore import QThread, pyqtSignal, QTimer

from subprocess import Popen, PIPE


class CmdExecutor(QThread):
    """
    CMD命令执行的子线程
    """
    # 线程结束信号,信号包含内容都是一个list
    finishSignal = pyqtSignal(str)

    def __init__(self, parent=None):
        super(CmdExecutor, self).__init__(parent)
        # 执行命令
        self.cmd = None
        # 连接计数器
        self._intConnectTime = 0
        self._lastCallback = None
        self._initConnectTimer()
        # 线程结束的事件绑定
        self.finished.connect(self.threadFinish)

    def _initConnectTimer(self):
        self.timer = QTimer()
        self.timer.timeout.connect(self._onCmdExectuedTimeout)

    def _onCmdExectuedTimeout(self):
        """
        cmd执行超时的槽函数
        :return:
        """
        if self._intConnectTime >= 20:  # 超过20s
            self.requestInterruption()  # 请求终止线程
            self.timer.stop()
            self.finishSignal.emit('cmdExectuedTimeout')  # 发送超时信号
        else:
            self._intConnectTime = self._intConnectTime + 1

    def exec(self, cmd):
        self.cmd = cmd
        self._intConnectTime = 0
        self.timer.start(1000)
        self.start()

    def threadFinish(self):
        if self.isRunning():
            self.wait()
        # print(self.isFinished())
        # 发送正常结束的信号
        self.finishSignal.emit(self.result)

    def run(self):
        # 日志输出文件初始化 --- Start
        _process = Popen(self.cmd, stdout=PIPE, bufsize=-1, encoding='utf-8')
        stdout_data, stderr_data = _process.communicate(input=None, timeout=None)
        if stderr_data is not None:
            # print("CmdExecutor stderr_data = " + stderr_data)
            self.result = stderr_data.strip()
        if stdout_data is not None:
            # print("CmdExecutor stdout_data = " + stdout_data)
            self.result = stdout_data.strip()
            # self.result = stdout_data.strip().split('\n')
        # for line in iter(_process.stdout.readline, b''):
        #     l.append(line.decode('utf-8'))
        #     # print("aaaaaaaaaaaaaa : "+line.decode('utf-8'))
        _process.stdout.close()   # close触发finish?
        # _process.wait()
        if self.isInterruptionRequested():  # 判断是否请求终止线程
            return
        self.timer.stop()

    def setFinishCallback(self, get_slot):
        if self._lastCallback is not None:
            self.finishSignal.disconnect(self._lastCallback)
        self.finishSignal.connect(get_slot)  # 连接信号与槽
        self._lastCallback = get_slot
