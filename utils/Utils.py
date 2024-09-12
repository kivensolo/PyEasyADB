#!/usr/bin/python
# -*- coding: utf-8 -*-
import datetime
import hashlib
import logging
import os
import sqlite3

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
    def get_file_info(file_path):
        """
        获取文件信息
        :param file_path:
        :return:
        """
        try:
            # 文件名称
            file_name  = os.path.basename(file_path)
            # 文件大小(字节)
            file_size = os.stat(file_path).st_size

            # 获取文件的最后修改时间
            last_modified_time = os.path.getmtime(file_path)
            last_modified_date = datetime.datetime.fromtimestamp(last_modified_time)

            # 获取文件的创建时间
            creation_time = os.path.getctime(file_path)
            creation_date = datetime.datetime.fromtimestamp(creation_time)

            md5 = FileUtils.calculate_md5(file_path)

            return {
                "file_name": file_name,
                "file_bytes":  file_size,
                "file_md5": md5,
                "last_modified": last_modified_date.strftime("%Y-%m-%d %H:%M:%S"),
                "creation": creation_date.strftime("%Y-%m-%d %H:%M:%S")
            }
        except Exception as e:
            z_logger.error(f'文件信息读取失败,{file_name}:{str(e)}')
            return {
                    "file_name": "",
                    "file_bytes": 0,
                    "file_md5": "",
                    "last_modified": "",
                    "creation": ""
                }
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
