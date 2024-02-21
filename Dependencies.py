import os
import sys
import threading
import zipfile

from PyQt5.QtCore import pyqtSignal, QObject, QTimer
from PyQt5.QtWidgets import QMessageBox
from pip._vendor import requests

from src import settings
from src.logcat.log import z_logger


def downloadFiles(url, filePath):
    """
    下载制定文件到指定目录
    :param url:
    :param filePath:
    :return:
    """
    if os.path.exists(filePath) and (os.path.getsize(filePath) != 0):
        z_logger.debug("文件存在,无需下载")
        return True, "文件存在,无需下载"

    z_logger.debug(f"开始下载文件:{url}")
    try:
        response = requests.get(url)
        with open(filePath, "wb") as f:
            f.write(response.content)
        z_logger.debug("下载完成")
        return True, "下载完成"
    except Exception as e:
        return False, e

def extractFiles(srcPath, outPath):
    """
    解压文件至指定目录
    :param srcPath: 压缩文件来源
    :param outPath: 输出目录
    :return:
    """
    with zipfile.ZipFile(srcPath, "r") as zip_ref:
        # extract all
        for file in zip_ref.namelist():
            try:
                zip_ref.extract(file, outPath)
            except Exception:
                z_logger.debug(f"解压失败的文件： {file}")
    z_logger.debug("解压完成")


def addEnvVariable(newEnvVar):
    oldEnv = os.environ['PATH']
    if newEnvVar in oldEnv:
        return
    os.environ['PATH'] = f'{oldEnv}{newEnvVar};'


class AndroidDependencies:
    downloadedZipFile = os.path.join(settings.localAppDataOfEasyADB, "platform-tools-latest-windows.zip")
    tmpPlatformPath = settings.platformToolsPath
    # 平台工具最新集合包
    downloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    # 目前在使用的依赖库文件
    dependenciesFiles = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "fastboot.exe", "libwinpthread-1.dll"]

    def Check(self):
        addEnvVariable(self.tmpPlatformPath)
        if self.isFilesExist():
            return
        result = self.__showWarning()
        if result == QMessageBox.Ok:
            # 下载压缩工具包
            downloadFiles(self.downloadUrl, self.downloadedZipFile)

            # check 'platform-tools' dir
            if not os.path.exists(self.tmpPlatformPath):
                os.mkdir(self.tmpPlatformPath)

            # 对工具包进行解压
            extractFiles(self.downloadedZipFile, settings.localAppDataOfEasyADB)

            self.__showComplated()
        elif result == QMessageBox.Cancel:
            sys.exit(0)

    def isFilesExist(self):
        for fileName in self.dependenciesFiles:
            filePath = os.path.join(self.tmpPlatformPath, fileName)
            if not os.path.exists(filePath):
                return False
        return True

    def __showWarning(self):
        message_box = QMessageBox()
        message_box.setIcon(QMessageBox.Warning)
        message_box.setWindowTitle("警告")
        message_box.setText("必要文件缺失,是否进行下载？")
        message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
        dialogResult = message_box.exec_()
        return dialogResult

    def __showComplated(self):
        message_box = QMessageBox()
        message_box.setIcon(QMessageBox.Information)
        message_box.setWindowTitle("完成")
        message_box.setText("文件下载完毕!")
        message_box.setStandardButtons(QMessageBox.Ok)
        dialogResult = message_box.exec_()
        return dialogResult


class ScrcpyChecker(QObject):
    # 默认自动下载的scrcpy 版本(released on Dec 2, 2023)
    # 最新版本查看 https://github.com/Genymobile/scrcpy/releases/latest
    _version = 'v2.3.1'
    toolsPath = settings.toolsPath

    downloadedZipFile = os.path.join(toolsPath, f"scrcpy-win64-{_version}.zip")
    scrcpyPath = os.path.join(toolsPath, "scrcpy-win64")
    scrcpyExeFilePath = os.path.join(scrcpyPath, "scrcpy.exe")
    downloadUrl = f'https://github.com/Genymobile/scrcpy/releases/download/{_version}/scrcpy-win64-{_version}.zip'

    checkFinished = pyqtSignal(tuple)

    def __init__(self):
        super().__init__()
        self.thread = threading.Thread(target=self.__check)
        self.thread.name = 'ScrcpyChecker'
    def __check(self):
        if not os.path.exists(self.downloadedZipFile):
            self.downloadTimer = QTimer()
            self.downloadTimer.timeout.connect(self.onTimerFinish)
            result, desc = downloadFiles(self.downloadUrl, self.downloadedZipFile)
            if not result:
                self.checkFinished.emit((False, f'{desc}'))
                return
        # 对工具包进行解压
        z_logger.info(f"依赖组件下载完毕, 释放中....")
        extractFiles(self.downloadedZipFile, self.toolsPath)
        extractPath = os.path.join(self.toolsPath, f'scrcpy-win64-{self._version}')
        if os.path.exists(extractPath):
            os.rename(extractPath, self.scrcpyPath)
        z_logger.info(f"释放完毕,即将启动.....")
        self.checkFinished.emit((True, self.scrcpyExeFilePath))

    def onTimerFinish(self):
        self.checkFinished.emit((False, '下载超时!建议科学上网后，再进行尝试。'))

    def startCheck(self):
        """
          检查scrcpy目录是否存在
          :return:
          """
        addEnvVariable(self.scrcpyPath)

        if os.path.exists(self.scrcpyPath):
            self.checkFinished.emit((True, self.scrcpyExeFilePath))
            return

        # check 'tools' dir
        if not os.path.exists(self.toolsPath):
            os.mkdir(self.toolsPath)

        result = self.__showWarning()
        if result == QMessageBox.Ok:
            z_logger.error("开始下载依赖组件,耗时会受网络因素影响，请耐心等待.......")
            self.thread.start()
        elif result == QMessageBox.Cancel:
            z_logger.info("下载取消")
            self.checkFinished.emit((False, ""))

    def __showWarning(self):
        message_box = QMessageBox()
        message_box.setIcon(QMessageBox.Warning)
        message_box.setWindowTitle("警告")
        message_box.setText("检测到缺少镜像功能需要的依赖组件\n是否进行自动下载？")
        message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
        dialogResult = message_box.exec_()
        return dialogResult