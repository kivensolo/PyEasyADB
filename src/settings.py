import os

# 使用的目录配置  当前脚本目录
# BASE_PATH = sys.argv[0]   xxxx/PyQt5Demo/App.py
import time

""" 依赖环境检测配置 """
appdata_local = os.environ['LOCALAPPDATA']
localAppDataOfEasyADB = os.path.join(appdata_local, 'EasyADB')
if not os.path.exists(localAppDataOfEasyADB):
    os.mkdir(localAppDataOfEasyADB)
platformToolsPath = os.path.join(localAppDataOfEasyADB, "platform-tools")
toolsPath = os.path.join(localAppDataOfEasyADB, "tools")
""" 依赖环境检测配置 """

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

# Scrcpy应用路径
SCRCPY_PATH = os.path.join(BASE_PATH, 'tool', 'scrcpy-win64')

# 应用数据库路径及名称
APP_DB_FILE = "%s\\data\\easyADB.db" % BASE_PATH

APP_VERSION = '1.0.2'

DEBUG_PRINT = True

# QSetting的缓存Key
key_app_activity_classpath = "line_edit_classpath"
key_app_action = "line_edit_action"
key_app_extparams = "edit_ext_params"

"""
实时Logcat配置
"""
# 默认是否只展示选中进程日志
LIVE_LOG_DEFAULT_FILTER_PID = False
# 日志上限条数
LIVE_LOG_CONUTS_LIMITS = 3000

