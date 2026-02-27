import os
import sys
import threading
import zipfile
import subprocess
import re

from PyQt5.QtCore import pyqtSignal, QObject, QTimer, pyqtSlot
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
    if os.path.exists(filePath) and (os.path.getsize(filePath) > 1024):
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

            # 删除下载后的压缩文件
            try:
                os.remove(self.downloadedZipFile)
            except Exception as e:
                z_logger.debug(f"删除platform-tools压缩包失败：{e}")

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
    # 默认自动下载的scrcpy 版本
    # 最新版本查看 https://github.com/Genymobile/scrcpy/releases/latest
    _version = 'v3.3.4'
    toolsPath = settings.toolsPath

    downloadedZipFile = os.path.join(toolsPath, f"scrcpy-win64-{_version}.zip")
    scrcpyPath = os.path.join(toolsPath, "scrcpy-win64")
    scrcpyExeFilePath = os.path.join(scrcpyPath, "scrcpy.exe")
    # https://github.com/Genymobile/scrcpy/releases/download/v3.3.4/scrcpy-win64-v3.3.4.zip
    downloadUrl = f'https://github.com/Genymobile/scrcpy/releases/download/{_version}/scrcpy-win64-{_version}.zip'

    checkFinished = pyqtSignal(tuple)

    def __init__(self):
        super().__init__()
        self.thread = None

    def getLocalScrcpyVersion(self):
        """
        获取本地已安装的 scrcpy 版本
        :return: 版本字符串，如 'v2.3.1'，如果无法获取则返回 None
        """
        if not os.path.exists(self.scrcpyExeFilePath):
            return None

        try:
            # 方法1: 通过 scrcpy.exe --version 获取版本
            result = subprocess.run(
                [self.scrcpyExeFilePath, '--version'],
                capture_output=True,
                text=True,
                timeout=5
            )
            output = result.stdout.strip()
            # 输出格式类似: "scrcpy 2.3.1" 或 "scrcpy 2.3"
            match = re.search(r'scrcpy\s+(\d+\.\d+(?:\.\d+)?)', output)
            if match:
                version_num = match.group(1)
                return f'v{version_num}'
        except Exception as e:
            z_logger.debug(f"通过 --version 获取版本失败: {e}")

        try:
            # 方法2: 通过文件属性获取版本信息
            # 如果目录名包含版本号（如 scrcpy-win64-v2.3.1），从中提取
            parent_dir = os.path.dirname(self.scrcpyPath)
            for item in os.listdir(parent_dir):
                if 'scrcpy-win64' in item and item != 'scrcpy-win64':
                    match = re.search(r'scrcpy-win64-?(v?\d+\.\d+(?:\.\d+)?)', item)
                    if match:
                        return match.group(1)
        except Exception as e:
            z_logger.debug(f"通过目录名获取版本失败: {e}")

        return None

    def compareVersions(self, local_version, target_version):
        """
        比较两个版本号
        :param local_version: 本地版本，如 'v2.3.1' 或 '2.3.1'
        :param target_version: 目标版本，如 'v3.3.4' 或 '3.3.4'
        :return: True 表示本地版本低于目标版本，需要升级
        """
        def version_tuple(v):
            # 移除 'v' 前缀并分割版本号
            v = v.lstrip('v')
            parts = v.split('.')
            # 补齐到3位，例如 2.3 -> 2.3.0
            while len(parts) < 3:
                parts.append('0')
            return tuple(int(p) for p in parts)

        try:
            return version_tuple(local_version) < version_tuple(target_version)
        except Exception as e:
            z_logger.debug(f"版本比较失败: {e}")
            # 如果比较失败，默认需要升级
            return True

    def removeOldScrcpy(self):
        """
        删除旧的 scrcpy 目录
        :return: 是否删除成功
        """
        try:
            if os.path.exists(self.scrcpyPath):
                import shutil
                shutil.rmtree(self.scrcpyPath)
                z_logger.info(f"已删除旧版本的 scrcpy 目录: {self.scrcpyPath}")
                return True
        except Exception as e:
            z_logger.error(f"删除旧版本 scrcpy 失败: {e}")
            return False
        return False

    def _check(self):
        """
        内部检查方法，在线程中执行
        """
        self.isDownloadTimeOut = False

        # 删除旧的 zip 文件（如果存在）
        if os.path.exists(self.downloadedZipFile):
            try:
                os.remove(self.downloadedZipFile)
            except Exception as e:
                z_logger.debug(f"删除旧 zip 文件失败: {e}")

        QTimer.singleShot(10 * 1000, self.onTimerFinish)
        result, desc = downloadFiles(self.downloadUrl, self.downloadedZipFile)
        if not self.isDownloadTimeOut:
            if not result:
                self.checkFinished.emit((False, f'{desc}'))
                return
        else:
            return

        # 删除旧版本目录
        self.removeOldScrcpy()

        # 对工具包进行解压
        z_logger.info(f"依赖组件下载完毕, 释放中....")
        extractFiles(self.downloadedZipFile, self.toolsPath)
        extractPath = os.path.join(self.toolsPath, f'scrcpy-win64-{self._version}')
        if os.path.exists(extractPath):
            os.rename(extractPath, self.scrcpyPath)
        z_logger.info(f"释放完毕,即将启动.....")
        self.checkFinished.emit((True, self.scrcpyExeFilePath))

    @pyqtSlot()
    def onTimerFinish(self):
        self.isDownloadTimeOut = True
        self.checkFinished.emit((False, '下载超时!建议科学上网后，再进行尝试。或者进行离线安装。'))

    def startCheck(self):
        """
        检查 scrcpy 组件是否存在及版本
        - 如果不存在：提示下载
        - 如果版本过低：提示升级
        :return:
        """
        addEnvVariable(self.scrcpyPath)

        # 检查目录是否存在
        if not os.path.exists(self.scrcpyPath):
            return self._showDownloadDialog("首次使用", is_upgrade=False)

        # 检查版本是否需要升级
        local_version = self.getLocalScrcpyVersion()
        if local_version is None:
            # 无法获取版本，但目录存在，直接使用
            z_logger.debug("无法获取本地 scrcpy 版本，跳过版本检查")
            self.checkFinished.emit((True, self.scrcpyExeFilePath))
            return

        # 比较版本
        if self.compareVersions(local_version, self._version):
            return self._showDownloadDialog("镜像组件升级", is_upgrade=True, old_version=local_version)

        # 版本符合要求
        self.checkFinished.emit((True, self.scrcpyExeFilePath))

    def _showDownloadDialog(self, title_prefix, is_upgrade=False, old_version=None):
        """
        显示下载/升级对话框
        :param title_prefix: 对话框标题前缀
        :param is_upgrade: 是否为升级操作
        :param old_version: 旧版本号（升级时使用）
        """
        if is_upgrade and old_version:
            message = f"镜像组件内置版本高级本地版本\n本地版本: {old_version}\n当前版本: {self._version}\n\n是否进行升级下载？(建议开启科学上网)"
        else:
            message = "检测到缺少镜像功能需要的依赖组件\n是否进行自动下载？(建议开启科学上网)"

        message_box = QMessageBox()
        message_box.setIcon(QMessageBox.Warning)
        message_box.setWindowTitle(title_prefix)
        message_box.setText(message)
        message_box.setStandardButtons(QMessageBox.Ok | QMessageBox.Cancel)
        dialogResult = message_box.exec_()

        if dialogResult == QMessageBox.Ok:
            z_logger.info_with_stamp("开始下载依赖组件,耗时会受网络因素影响，请耐心等待.......")
            self.thread = threading.Thread(target=self._check)
            self.thread.name = 'ScrcpyChecker'
            self.thread.start()
        elif dialogResult == QMessageBox.Cancel:
            if is_upgrade:
                z_logger.info_with_stamp("取消升级，使用现有版本")
                # 升级被取消，但旧版本仍然可用
                self.checkFinished.emit((True, self.scrcpyExeFilePath))
            else:
                z_logger.info_with_stamp("下载取消")
                self.checkFinished.emit((False, "手动取消"))