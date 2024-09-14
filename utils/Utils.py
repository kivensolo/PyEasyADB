#!/usr/bin/python
# -*- coding: utf-8 -*-
import datetime
import hashlib
import logging
import os
import re
import sqlite3
import subprocess

from src.logcat.log import z_logger


# import xmltodict
# from PyQt5.QtWebEngineWidgets import QWebEngineScript

# from XulDebugTool.logcatapi.Logcat import STCLogger


class Utils(object):
    windowWidth = 1920
    windowHeight = 1080
    itemHeight = 30

    # @staticmethod
    # def xml2json(xml, tag):
    #     # 将xml的某个tag转化成json
    #     doc = xmltodict.parse(xml)[tag]
    #     if doc != None:
    #         str = json.dumps(dict(doc))
    #         return json.loads(str)
    #     else:
    #         return ''

    # @staticmethod
    # def scriptCreator(path, name, page):
    #     script = QWebEngineScript()
    #     f = open(path, 'r')
    #     script.setSourceCode(f.read())
    #     script.setInjectionPoint(QWebEngineScript.DocumentReady)
    #     script.setName(name)
    #     script.setWorldId(QWebEngineScript.MainWorld)
    #     page.scripts().insert(script)

    # @staticmethod
    # def findNodeById(id, xml):
    #     root = etree.fromstring(xml)
    #     # print(etree.tostring(root, pretty_print=True).decode('utf-8'))
    #     try:
    #         list = root.xpath("//*[@id=%s]" % id)
    #     except Exception as e:
    #         STCLogger().e(e)
    #     return list[0]

    @staticmethod
    def init(rect):
        Utils.windowWidth = rect.width()
        Utils.windowHeight = rect.width()

    @staticmethod
    def setWindowWidth(width):
        Utils.windowWidth = width

    @staticmethod
    def setWindowHeight(height):
        Utils.windowHeight = height
        Utils.itemHeight = int(Utils.windowHeight / 36.0)

    @staticmethod
    def getWindowWidth():
        return Utils.windowWidth

    @staticmethod
    def getWindowHeight():
        return Utils.windowHeight

    @staticmethod
    def getItemHeight():
        return Utils.itemHeight

    @staticmethod
    def setAutoLoginState(loginState):
        try:
            conn = sqlite3.connect('XulDebugTool.db')
            cursor = conn.cursor()
            cursor.execute("delete from login")
            cursor.execute("insert into login (name) values ('" + str(loginState) + "')")
            conn.commit()
        except Exception as e:
            print(e)
        finally:
            cursor.close()
            conn.close()

    @staticmethod
    def getAutoLoginState():
        try:
            conn = sqlite3.connect('XulDebugTool.db')
            cursor = conn.cursor()
            cursor.execute('select * from login')
            result = cursor.fetchone()
        except Exception:
            return []
        finally:
            cursor.close()
            conn.close()
        return result


_nameToLevel = {
    'Verbose': logging.NOTSET,
    'Debug': logging.DEBUG,
    'Info': logging.INFO,
    'Warn': logging.WARNING,
    'Error': logging.ERROR,
    'Assert': logging.CRITICAL
}

_simpleNameToLevel = {
    "D": logging.DEBUG,
    "I": logging.INFO,
    "W": logging.WARNING,
    "E": logging.ERROR,
    "A": logging.CRITICAL
}

_filterOptions = [
    "No Filter",
    "Show only selected application"
]


class LogUtils(object):
    @staticmethod
    def changeLogColor(appen_prefix, level, log):
        _color_log = log

        if appen_prefix:                # 蓝
            _color_log = "<font color=\"#005ac7\" >{0}</font>".format(log)
            _color_log = str(_color_log).replace("\n", "<br>")
            return _color_log

        if level >= logging.ERROR:      # 红
            _color_log = "<font color=\"#bf360c\">{0}</font>".format(log)
        elif level == logging.WARNING:  # 黄
            _color_log = "<font color=\"#b07805\">{0}</font>".format(log)
        elif level == logging.INFO:     # 黑
            _color_log = "<font color=\"#263238\" >{0}</font>".format(log)
        elif level == logging.DEBUG:    # 绿
            _color_log = "<font color=\"#388e3c\">{0}</font>".format(log)

        # 解决该控件插入Html时，不支持\n的问题
        _color_log = str(_color_log).replace("\n", "<br>")
        # 文字后加换行符，准备下一次输出(注意必须要有一个空格，否则不生效)
        # _color_log = _color_log + "<br />"
        return _color_log

    @staticmethod
    def build_time_stamp():
        import time
        ct = time.time()
        local_time = time.localtime(ct)
        data_head = time.strftime("%Y-%m-%d %H:%M:%S", local_time)
        data_secs = (ct - int(ct)) * 1000
        time_stamp = "%s.%03d" % (data_head, data_secs)
        return time_stamp + ": "

    @staticmethod
    def highlight_link_addr(text):
        if isinstance(text, str):
            import re
            # FIXME 匹配  http://imgzm.qun7.com/uploads/20230117/63c66916d79e9.jpg!webp_____position:2   失败
            regexUrl = re.compile(r"http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*,]|(?:%[0-9a-fA-F][0-9a-fA-F]))+(?:\.jpg|\.jpeg|\.png|\.gif|\.bmp|\.webp)*",
                                  re.IGNORECASE)
            urls = regexUrl.findall(text)
            for url in urls:
                preS = "<a href=\"" + url + "\">" + url + "</a>"
                text = text.replace(url, preS)
        return text


class FileUtils(object):

    @staticmethod
    def calculate_md5(file_path, chunk_size=4096):
        """
        计算文件的 MD5 值。

        :param file_path: 文件路径
        :param chunk_size: 读取文件的块大小，默认为 4096 字节
        :return: 文件的 MD5 值
        """
        md5_hash = hashlib.md5()

        with open(file_path, 'rb') as file:
            while True:
                data = file.read(chunk_size)
                if not data:
                    break
                md5_hash.update(data)

        return md5_hash.hexdigest()

    @staticmethod
    def parse_apk(file_path: str):

        """
        解析APK文件并获取相关信息。

        :param file_path: APK文件路径
        :return: 包含应用包名、应用名称、证书MD5、版本号、图标路径和权限列表的字典
        """
        try:
            # 文件名称
            file_name  = os.path.basename(file_path)
            # 文件大小(字节)
            file_size = os.stat(file_path).st_size

            # 获取文件的最后修改时间
            last_modified_time = os.path.getmtime(file_path)
            last_modified_date = datetime.datetime.fromtimestamp(last_modified_time)
            last_modified_format = last_modified_date.strftime("%Y-%m-%d %H:%M:%S")

            # 获取文件的创建时间
            creation_time = os.path.getctime(file_path)
            creation_date = datetime.datetime.fromtimestamp(creation_time)
            creation_format = creation_date.strftime("%Y-%m-%d %H:%M:%S")
            md5 = FileUtils.calculate_md5(file_path)
        except Exception as e:
            z_logger.error(f'文件信息读取失败,{file_name}:{str(e)}')
            return {
                "file_name": "",
            }

        # 获取当前操作系统
        current_os = os.name
        # 根据当前操作系统选择换行符
        if current_os == 'nt':  # Windows系统
            line_break = '\r\n'
        elif current_os == 'posix':  # Linux、Unix-like系统
            line_break = '\n'
        else:  # 其他操作系统，默认使用换行符'\n'
            line_break = '\n'

        package_name = ""
        version_code = ""
        version_name = ""
        cert_md5 = ""
        cert_md5_version = []
        icon_path = ""
        min_sdk = ""
        # 获取权限列表
        permissions = []

        commands = [
            ['aapt', 'dump', 'badging', file_path],
            ['apksigner.bat', 'verify', '--print-certs', '-v', file_path]
        ]
        # 使用aapt命令获取APK信息
        command = commands[0]
        try:
            result = subprocess.run(command, capture_output=True)
            output = result.stdout.decode('utf-8', 'ignore')
            lines = output.split(line_break)
            for line in lines:
                if line.startswith("package:"):
                    package_name = re.search(r"package: name='(.*?)'", line).group(1)
                    # 获取版本号
                    version_code = re.search(r"versionCode='(.*?)'", line).group(1)
                    # 获取版本名称
                    version_name = re.search(r"versionName='(.*?)'", line).group(1)
                elif line.startswith("sdkVersion:"):
                    min_sdk = re.search(r"sdkVersion:'(.*?)'", line).group(1)
                elif line.startswith("application: label="):
                    package_name = re.search(r"application: label='(.*?)'", line).group(1)
                elif line.startswith("uses-permission:"):
                    permissions.append(re.search(r"uses-permission: name='(.*?)'", line).group(1))
        except Exception as e:
            z_logger.debug(f"Error running AAPT command: {e}")

        # 使用apksigner命令获取APK签名信息 注意，这里只能使用“apksigner.bat”
        command = commands[1]
        try:
            # process = subprocess.Popen(command, shell=False, stdout=subprocess.PIPE,
            #                                 stderr=subprocess.PIPE, bufsize=-1, encoding='utf-8')
            # stdout_data, stderr_data = process.communicate(input=None, timeout=None)
            result = subprocess.run(command, capture_output=True)
            stdout_data = result.stdout.decode('utf-8', 'ignore')
            if stdout_data:
                lines = stdout_data.split('\n')
                for line in lines:
                    if line.startswith("Signer #1 certificate MD5 digest:"):
                        cert_md5 = re.search(r"Signer #1 certificate MD5 digest: (.*)", line).group(1).upper()
                    elif line.startswith("Verified using v"):
                        matches = re.findall(r"Verified using v(\d+) scheme.*: (\w+)", line)
                        for match in matches:
                            version, value = match
                            if value == "true":
                                cert_md5_version.append(f"v{version}")
        except Exception as e:
            z_logger.debug(f"Error running ApkSigner command: {e}")
            cert_md5 = f"解析失败:{e}"
            cert_md5_version = ["N/A"]

        return {
            "package_name": package_name,
            "app_name": version_name,
            "sign_md5": cert_md5,
            "sign_md5_version": cert_md5_version,
            "version_code": version_code,
            "version_name": version_name,
            "min_sdk": min_sdk,
            "icon_path": icon_path,
            "permissions": permissions,

            "file_name": file_name,
            "file_bytes":  file_size,
            "file_md5": md5,
            "last_modified": last_modified_format,
            "creation": creation_format
        }
