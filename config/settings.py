import os

# 使用的目录配置  当前脚本目录
# BASE_PATH = sys.argv[0]   xxxx/PyQt5Demo/App.py
import time

BASE_PATH = os.getcwd()

# 日志配置
LOGS_PATH = "%s\\logs" % BASE_PATH
LOG_CMD_OUT = "pro_{0}.log".format(time.strftime("%Y-%m-%d"))
LOG_CMD_ERROR = "pro_{0}.err".format(time.strftime("%Y-%m-%d"))
LOG_APP_FILE = os.path.join(LOGS_PATH, ".".join(["log_esay_adb", "log"]))

# 应用数据库名
DB_NAME = "easyADB.db"

APP_VERSION = '1.0'

# 资源文件
PATH_LOGO_ICON = 'res/img/logo.png'


DEBUG_PRINT = True

