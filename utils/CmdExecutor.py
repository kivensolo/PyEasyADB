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
        self.process = None
        self.result = ""
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
            self.process.kill()
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
        self.process = Popen(self.cmd, stdout=PIPE,stderr=PIPE, bufsize=-1, encoding='utf-8')
        stdout_data, stderr_data = self.process.communicate(input=None, timeout=None)
        if stdout_data is not None:
            # 把多行换行符换成一行(ps命令会有多行)
            stdout_data = stdout_data.replace("\n\n", "\n")
            # 正常输出的结果不进行strip操作
            self.result = stdout_data.strip()
        if stderr_data is not None and stderr_data != "":
            self.result = self.result + "\n" + stderr_data.strip()

        self.process.stdout.close()   # close触发finishSignal
        # _process.wait()
        if self.isInterruptionRequested():  # 判断是否请求终止线程
            return
        self.timer.stop()

    def setFinishCallback(self, get_slot):
        if self._lastCallback is not None:
            # 断开上一次的回调信号槽
            self.finishSignal.disconnect(self._lastCallback)
            self._lastCallback = None
        if get_slot is not None:
            # 连接新的信号槽
            self.finishSignal.connect(get_slot)
            self._lastCallback = get_slot
