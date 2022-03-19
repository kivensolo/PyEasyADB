"""
desc:   logger 类封装
"""

import logging
import logging.handlers
import os


# 创建对应文件夹
from config.settings import LOGS_PATH, DEBUG_PRINT, LOG_APP_FILE

if not os.path.exists(LOGS_PATH):
    os.makedirs(LOGS_PATH)


def d(msg=''):
    if DEBUG_PRINT:
        print(msg)
    z_logger.info(msg)


def e(msg=''):
    if DEBUG_PRINT:
        print(msg)
    z_logger.error(msg)


log_format = "%(name)s %(levelname)s %(asctime)s (%(filename)s: %(lineno)d) - %(message)s"


class Logger:
    logger_map = {}

    def __init__(self, name="", filename="",
                 level=logging.INFO, logFormat=log_format,
                 maxBytes=4194304, backup_num=128, cache=True):
        self.name = name
        self.filename = filename
        self._logger = logging.getLogger(self.name)
        self._logger.setLevel(level)

        if self.name and self.name in Logger.logger_map.keys():
            self._logger = Logger.logger_map[self.name]
        else:
            if self.filename:
                formatter = logging.Formatter(logFormat)
                rotatingFileHandler = logging.handlers.RotatingFileHandler(self.filename,
                                                                           maxBytes=maxBytes,
                                                                           backupCount=backup_num)
                rotatingFileHandler.formatter = formatter
                self._logger.addHandler(rotatingFileHandler)
            else:
                logging.basicConfig(format=logFormat)
            if cache:
                Logger.logger_map[name] = self._logger

        self.info = self._logger.info
        self.warn = self._logger.warning
        self.error = self._logger.error
        self.critical = self._logger.critical

    def addConsoleHandler(self):
        if len(self._logger.handlers) != 2:
            consoleHandler = logging.StreamHandler()
            formatter = logging.Formatter(log_format)
            consoleHandler.formatter = formatter
            self._logger.addHandler(consoleHandler)

    def remove(self):
        Logger.logger_map.pop(self.name)

    def modify_rotating(self, maxBytes=None, backupCount=None):
        ro_handler = self._logger.handlers[0]
        if maxBytes:
            ro_handler.maxBytes = maxBytes
        if backupCount:
            ro_handler.backupCount = backupCount


z_logger = Logger("Zlogger", LOG_APP_FILE)
