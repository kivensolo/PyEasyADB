import os
import sys
import zipfile

from PyQt5.QtWidgets import QMessageBox
from pip._vendor import requests

from src import settings
from src.logcat.log import z_logger


class Dependencies:
    downloadedZipFile = os.path.join(settings.appEasyADBPath, "platform-tools-latest-windows.zip")
    tmpPlatformPath = settings.platformToolsPath
    # 平台工具最新集合包
    downloadUrl = "https://dl.google.com/android/repository/platform-tools-latest-windows.zip"
    # 目前在使用的依赖库文件
    dependenciesFiles = ["adb.exe", "AdbWinApi.dll", "AdbWinUsbApi.dll", "fastboot.exe", "libwinpthread-1.dll"]

    def Check(self):
        self.setEnvVariable()
        if self.isFilesExist():
            return
        result = self.__showWarning()
        if result == QMessageBox.Ok:
            self.__downloadFiles()
            self.__extractFiles()
            self.__showComplated()
        elif result == QMessageBox.Cancel:
            sys.exit(0)

    def __downloadFiles(self):
        if os.path.exists(self.downloadedZipFile) \
                and (os.path.getsize(self.downloadedZipFile) != 0):
            z_logger.debug("文件存在,无需下载")
            return
        z_logger.debug(f"下载文件:{self.downloadUrl}")
        response = requests.get(self.downloadUrl)
        with open(self.downloadedZipFile, "wb") as f:
            f.write(response.content)
        z_logger.debug("下载完成")

    def __extractFiles(self):
        if not os.path.exists(self.tmpPlatformPath):
            os.mkdir(self.tmpPlatformPath)
        with zipfile.ZipFile(self.downloadedZipFile, "r") as zip_ref:
            # extract all
            for file in zip_ref.namelist():
                try:
                    zip_ref.extract(file, self.appEasyADBPath)
                except Exception as e:
                    z_logger.debug(f"解压失败的文件： {file}")
        z_logger.debug("解压完成")

    def setEnvVariable(self):
        oldEnv = os.environ['PATH']
        toolsPath = self.tmpPlatformPath
        os.environ['PATH'] = f'{oldEnv}{toolsPath}'

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


class Scrcpy:
    _version = settings.defaultScrcpyDownloadVersion
    toolsPath = os.path.join(settings.appEasyADBPath, "tools")

    downloadedZipFile = os.path.join(toolsPath, f"scrcpy-win64-{_version}.zip")
    scrcpyPath = os.path.join(toolsPath, f"scrcpy-win64", "scrcpy.exe")
    downloadUrl = f'https://github.com/Genymobile/scrcpy/releases/download/{_version}/scrcpy-win64-{_version}.zip'

    def Check(self):
        self.setEnvVariable()
        if os.path.exists(self.scrcpyPath):
            return True
        result = self.__showWarning()
        if result == QMessageBox.Cancel:
            # self.__downloadFiles()
            # self.__extractFiles()
            # self.__showComplated()
            print(f"下载Scrcpy:{self.downloadUrl}")

        elif result == QMessageBox.OK:
            # 设置路径
            print("设置路径")

    def setEnvVariable(self):
        oldEnv = os.environ['PATH']
        if self.toolsPath in oldEnv:
            return
        toolsPath = self.toolsPath
        os.environ['PATH'] = f'{oldEnv}{toolsPath}'

    def __showWarning(self):
        message_box = QMessageBox()
        message_box.setIcon(QMessageBox.Warning)
        message_box.setWindowTitle("警告")
        message_box.setText("环境缺少Scrcpy工具，是否进行默认版本下载？")
        # ok是选择自定义路径， 取消是进行下载
        message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
        dialogResult = message_box.exec_()
        return dialogResult