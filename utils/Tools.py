import multiprocessing
import os
import re
import subprocess
from pathlib import Path

from PyQt5.QtGui import QFont

from config.settings import LOG_CMD_OUT, LOG_CMD_ERROR, LOGS_PATH
from logcat import log


def getWRYHFontStyle(size=10):
    return QFont("微软雅黑", size)


def globalEditTextFontStyle():
    # Helvetica 字体， 比较细
    return QFont('Helvetica', 13)


def getKTFontStyle(size=12,font=QFont.Bold):
    return QFont("楷体", size, font)


def isIpMatches(ip):
    # ip格式判断检测
    return re.match("^((2[0-4]\d|25[0-5]|[01]?\d\d?)\.){3}(2[0-4]\d|25[0-5]|[01]?\d\d?)((:?)([0-9]{1,5})?)$",ip)


def printDevices():
    """
    使用 os.popen 执行cmd命令
    :return:
    """
    # popen返回文件对象，跟open操作一样
    f = os.popen(r"adb devices", "r")
    result = f.read()  # cmd输出结果
    f.close()
    print(result)

    # 输出结果字符串处理
    s = result.split("\n")  # 切割换行
    new = [x for x in s if x != '']  # 去掉空''
    print(new)

    # 可能有多个设备
    devices = []  # 获取设备名称
    for i in new:
        dev = i.split('\tdevice')
        if len(dev) >= 2:
            devices.append(dev[0])

    if not devices:
        print("手机没连上")
    else:
        print("当前手机设备:%s" % str(devices))


# TODO 查询命令的时候会卡主线程  因为用的是_process.wait
def exec_cmd(cmd, logger=None):
    log.d("exec cmd: "+cmd)
    """
    执行命令行，并返回输出
    :param cmd:  执行的命令行
    :return: 执行结果 字符串
    """
    default_timeout = 4000

    # 日志输出文件初始化 --- Start
    out_filepath = os.path.join(LOGS_PATH, LOG_CMD_OUT)
    error_filepath = os.path.join(LOGS_PATH, LOG_CMD_ERROR)

    # as_posix等同于replace('\\', '/')
    fdout = open(Path(out_filepath).as_posix(), 'w', encoding='utf-8')
    fderr = open(Path(error_filepath).as_posix(), 'w', encoding='utf-8')
    # 日志输出文件初始化 --- End

    _process = subprocess.Popen(cmd,
                                shell=True,
                                stdout=fdout,  # 输出到文件中
                                stderr=fderr,
                                encoding='utf-8')

    # print(_process.stderr.read()) #打印结果
    # for i in iter(_process.stdout.readline,'b'):
    # 针对实时打印的日志
    # print(i)

    try:
        return_code = _process.wait(timeout=default_timeout)
        log.d("result = {0}".format(return_code))
    except IOError as e:
        os.system("del/q %s %s" % (out_filepath, error_filepath))
        _process.kill()
        if logger:
            logger.info("End run command [%s] timeout -- %s" % (
                cmd, multiprocessing.current_process().name
            ))
        return False, "CMD [%s] timeout" % cmd

    # 进行信息处理
    with open(out_filepath, 'rb') as fdout:
        out = fdout.read()
    with open(error_filepath, 'rb') as fderr:
        error = fderr.read()

    try:
        out = out.decode("utf-8")
        error = error.decode("utf-8")
    except Exception as e:
        try:
            out = out.decode("gbk")
            error = error.decode("gbk")
        except Exception:
            try:
                out = out.decode("ISO-8859-2")
                error = error.decode("ISO-8859-2")
            except Exception:
                out = u"%s" % [out]
                error = u"%s" % [error]

    # os.system("del/q %s %s" % (out_filepath, error_filepath))  原来为啥要先放到文件中在再从文件中读出来？

    # printCmdLog(cmd, logger, error, out, return_code)

    fdout.close()
    fderr.close()

    if return_code != 0:
        status = False
        msg = error.strip("\n")  # TODo 后期进行详细错误描述
    else:
        status = True
        msg = out.strip("\n")  # TODo 后期进行详细错误描述

    return status, msg


def getDeviceInfo():
    # model = 'UnKnow'
    # version = '0.0'
    # sdk = 'UnKnow'

    _process = subprocess.Popen('adb shell getprop android.os.Build.MANUFACTURER',
                                shell=True,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE,
                                encoding='utf-8')
    manufacturer = _process.stdout.read().strip()  # 制造商
    _process.kill()

    _process = subprocess.Popen('adb shell getprop ro.product.model',
                                shell=True,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE,
                                encoding='utf-8')
    model = _process.stdout.read().strip()  # 设备型号
    _process.kill()

    _process = subprocess.Popen('adb shell getprop ro.build.version.release',
                                shell=True,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE,
                                encoding='utf-8')
    version = _process.stdout.read().strip()  # 系统版本号
    _process.kill()

    _process = subprocess.Popen('adb shell getprop ro.build.version.sdk',
                                shell=True,
                                stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE,
                                encoding='utf-8')
    sdk = _process.stdout.read().strip()    # api版本
    _process.kill()

    return "{0} {1} Android {2} API {3}".format(manufacturer, model, version, sdk)

    # status, _manufacturer = exec_cmd('adb shell getprop android.os.Build.MANUFACTURER')
    # if status:
    #     manufacturer = _manufacturer.strip()  # 制造商
    # else:
    #     return ''
    #
    # status, _model = exec_cmd('adb shell getprop ro.product.model')
    # if status:
    #     model = _model.strip()  # 设备型号
    #
    # status, _version = exec_cmd('adb shell getprop ro.build.version.release')
    # if status:
    #     version = _version.strip()  # 系统版本号
    #
    # status, _sdk = exec_cmd('adb shell getprop ro.build.version.sdk')
    # if status:
    #     sdk = _sdk.strip()  # api版本

    # return "{0} {1} Android {2} API {3}".format(manufacturer, model, version, sdk)


# 控制台打印命令行执行结果
def printCmdLog(cmd, logger, error, out, return_code):
    if logger:
        logger.info("--- return_code: [%s] cmd: [%s] ---" % (
            return_code, cmd
        ))

        logger.info("End run command [%s] -- result is [%s]\n[ERROR]: is [%s] -- %s" % (
            cmd, out, error, multiprocessing.current_process().name
        ))

# if __name__ == '__main__':
#     # print(exec_cmd("adb devices"))
#     result = re.split(":", "132.31.125.5:22")
#     print( result.__len__())
#     print(result)
#     print(result[1])





