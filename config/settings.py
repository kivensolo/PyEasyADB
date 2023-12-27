import os

# 使用的目录配置  当前脚本目录
# BASE_PATH = sys.argv[0]   xxxx/PyQt5Demo/App.py
import time

# 应用窗口屏占比
APP_SCREEN_RQTIO = 0.75

# 根目录
BASE_PATH = os.getcwd()

# 日志配置
LOGS_PATH = "%s\\data\\logs" % BASE_PATH

LOG_CMD_OUT = "pro_{0}.log".format(time.strftime("%Y-%m-%d"))
LOG_CMD_ERROR = "pro_{0}.err".format(time.strftime("%Y-%m-%d"))

_Log_NAME = "log_{0}.log".format(time.strftime("%Y-%m-%d"))
LOG_APP_FILE = os.path.join(LOGS_PATH, _Log_NAME)

# 应用数据库路径及名称
APP_DB_FILE = "%s\\data\\easyADB.db" % BASE_PATH

APP_VERSION = '1.0'

DEBUG_PRINT = True

