#!/usr/bin/python
# -*- coding: utf-8 -*-


import json
import logging
import sqlite3

# import xmltodict
# from PyQt5.QtWebEngineWidgets import QWebEngineScript
from lxml import etree

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