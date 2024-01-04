"""
desc:   logger 类封装
"""

import logging
import logging.handlers
import os

# 创建对应文件夹
from src.settings import LOGS_PATH, DEBUG_PRINT, LOG_APP_FILE

TIME_STAMP_PREFIX = "[STAMP]"

if not os.path.exists(LOGS_PATH):
    os.makedirs(LOGS_PATH)


# def w(*args):
#     z_logger.warn(args)
#     z_logger.judge_log_level(logging.WARNING, args)
#
#
# def d(*args):
#     z_logger.debug(args)
#     z_logger.judge_log_level(logging.DEBUG, args)
#
#
# def i(*args):
#     z_logger.info(args)
#     z_logger.judge_log_level(logging.INFO, args)
#
#
# def e(*args):
#     z_logger.error(args)
#     z_logger.judge_log_level(logging.ERROR, args)
#
#
# def c(*args):
#     z_logger.critical(args)
#     z_logger.judge_log_level(logging.CRITICAL, args)


log_format = "%(name)s %(levelname)s %(asctime)s (%(filename)s: %(lineno)d) - %(message)s"


class AppLogger:
    logger_map = {}

    def __init__(
            self, name="", filename="", level=logging.DEBUG,
            logFormat=log_format,
            maxBytes=4 * 1024 * 1024,  # 控制单个日志文件的大小，单位: 字节
            backup_num=128,    # 控制日志文件的数量，如果创建的日志文件数量多于这个数值，就删除最老的。
            cache=True):
        self.name = name
        self.filename = filename
        # 实例化Logger
        self._logger = logging.getLogger(self.name)
        # Set log level
        self._logger.setLevel(level)
        self.set_app_log_level(level)

        # log_formatter
        formatter = logging.Formatter(logFormat)

        if self.name and self.name in AppLogger.logger_map.keys():
            self._logger = AppLogger.logger_map[self.name]
        else:
            if self.filename:
                # Generate rolling logs, split logs based on file size
                rotatingFileHandler = logging.handlers.RotatingFileHandler(
                    self.filename, maxBytes=maxBytes, backupCount=backup_num, encoding='utf-8'
                )
                rotatingFileHandler.setLevel(logging.DEBUG)
                rotatingFileHandler.setFormatter(formatter)
                # add the file handlers to the logger
                self._logger.addHandler(rotatingFileHandler)
            else:
                logging.basicConfig(format=logFormat)

            # 建立streamhandler把日志打在CMD窗口上，级别为debug以上
            stream_handler = logging.StreamHandler()
            stream_handler.setLevel(logging.DEBUG)
            stream_handler.setFormatter(formatter)
            self._logger.addHandler(stream_handler)

            if cache:
                AppLogger.logger_map[name] = self._logger

        self.info = self._logger.info
        self.debug = self._logger.debug
        self.warn = self._logger.warning
        self.error = self._logger.error
        self.critical = self._logger.critical

    def info_with_stamp(self, content):
        """
        添加前缀标识，用于日志打印的时候输出时间戳
        :param content: 输出日志
        :return:
        """
        log = f"{TIME_STAMP_PREFIX}{content}"
        self.info(log)

    def remove(self):
        AppLogger.logger_map.pop(self.name)

    def add_gui_log_handler(self, view):
        """
        添加自定义的GUI log记录器
        :param view: 自定义的日志输出View
        :return: None
        """
        gui_handler = GuiLoggerHandler(view)
        # 使用logging的format
        # gui_handler.setFormatter(logging.Formatter(log_format))
        gui_handler.setLevel(logging.INFO)
        self._logger.addHandler(gui_handler)

    def modify_rotating(self, maxBytes=None, backupCount=None):
        """
        修改滚动日志的配置
        :param maxBytes:
        :param backupCount:
        :return:
        """
        ro_handler = self._logger.handlers[0]
        if maxBytes:
            ro_handler.maxBytes = maxBytes
        if backupCount:
            ro_handler.backupCount = backupCount

    def set_app_log_level(self, level):
        self.log_level = level

    def judge_log_level(self, level, args):
        if level >= self.log_level:
            WindowLogController.windowPrintInfo(level,args)
            if DEBUG_PRINT:
                print(args)
            pass
        else:
            return


class GuiLoggerHandler(logging.Handler):
    def __init__(self, logview):
        super().__init__(0)
        self.logView = logview

    """
    为UI控件提供的日志处理器, 此处相当于对系统日志做了一个代理层，将满足级别的日志，添加到编辑框中
    """
    def emit(self, record: logging.LogRecord):
        self.logView.append_log(self.format(record), record)


class WindowLogController:
    levelToName = {
        logging.CRITICAL: 'CRITICAL',
        logging.ERROR: 'ERROR',
        logging.WARNING: 'WARNING',
        logging.INFO: 'INFO',
        logging.DEBUG: 'DEBUG',
        logging.NOTSET: 'NOTSET',
    }

    @classmethod
    def windowPrintInfo(self, level, msg):
        mode = self.levelToName.get(level)
        windowStr = self.buildStandardTime(self) + " - Zlogger - " + mode + " : "

        # 输出到 ConsoleView
        print(windowStr, end='')
        for text in msg:
            print(self.getLogContent(self, text), end=' ')
        print("<br />")
        return

    def getLogContent(self, text):
        if isinstance(text, str):
            import re
            regexUrl = re.compile(r"http[s]?://(?:[a-zA-Z]|[0-9]|[$-_@.&+]|[!*,]|(?:%[0-9a-fA-F][0-9a-fA-F]))+",
                                  re.IGNORECASE)
            urls = regexUrl.findall(text)
            for url in urls:
                preS = "<a href=\"" + url + "\">" + url + "</a>"
                text = text.replace(url, preS)

        return text

    def buildStandardTime(self):
        import time
        ct = time.time()
        local_time = time.localtime(ct)
        data_head = time.strftime("%Y-%m-%d %H:%M:%S", local_time)
        data_secs = (ct - int(ct)) * 1000
        time_stamp = "%s.%03d" % (data_head, data_secs)
        return time_stamp


# 应用日志输出对象，目前输出至文件
z_logger = AppLogger("Zlogger", LOG_APP_FILE)
