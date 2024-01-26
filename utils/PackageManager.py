import re

from src.logcat.log import z_logger
from src.DataBase import DBManager
from utils.ADBTools import ADBTools


class PackageManager:
    """
    便捷的包信息管理器
    """
    __instance = None

    def __new__(cls):
        if not PackageManager.__instance:
            PackageManager.__instance = object.__new__(cls)
        return PackageManager.__instance

    def __init__(self):
        # 进程名称 可能是包名，也可能是“包名:子进程名”
        self.__selectedProcessNames = ""
        self.__selectedProcessPid = ""

        self.__selectedPackageNameInCustomActionComBox = ""
        self.dbManager = DBManager()
        self.adbTools = ADBTools()

    def setSelectedRunningProcessInfo(self, process_info):
        """
        设置选中的进程信息
        :param process_info: processName(pid)
        :return:
        """
        z_logger.debug("Set selected process:" + process_info)
        result = re.match(r'^([.:\w]+)\((\d+)\)$', process_info)
        if result:
            pid_name_info, pid_number = result.groups()
            # 注意:此处的pid_name_info可能是“主进程名”，也可能是“主进程名:子进程名”
            self.__selectedProcessNames = pid_name_info
            self.__selectedProcessPid = pid_number
        else:
            z_logger.error(f"未支持的进程信息,请反馈至开发者! {process_info}")

    def getSelectedProcessPid(self):
        return self.__selectedProcessPid

    def setSelectedPackageName(self, name):
        self.__selectedPackageNameInCustomActionComBox = name

    def getSelectedPackageName(self):
        return self.__selectedPackageNameInCustomActionComBox

    def isDbReady(self):
        return self.dbManager

    def exec(self, sql):
        return self.dbManager.exec_sql(sql)

    def query(self, column='*', table_name='default'):
        return self.dbManager.queryData(column, table_name)

    def addPackage(self, pkgName):
        """
        添加应用至数据库
        :param pkgName: 添加的应用包名
        :return:  [result, value]
        result: True|False
        """
        return self.dbManager.addPackageToDB(pkgName)

    def isPackageExist(self, pkgName):
        result = self.dbManager.getAppPackageByName(pkgName)
        package_info = result[1]
        return len(package_info) != 0

    def queryDeviceInfo(self, ip):
        return self.dbManager.get_device_prop_info(ip)

    def updateDeviceInfo(self, info, ip):
        return self.dbManager.update_device_prop(info, ip)[0]

    def updateDeviceAlias(self, ip, alias):
        return self.dbManager.update_device_alias(ip, alias)[0]


