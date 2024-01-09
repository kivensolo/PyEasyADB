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
        self.currentSelectedRunningProcessName = ""
        self.currentSelectedApp = ""
        self.dbManager = DBManager()
        self.adbTools = ADBTools()

    def setSelectedRunningProcessInfo(self, process_info):
        z_logger.debug("Set selected process:" + process_info)
        segments = str(process_info).split("(")
        self.currentSelectedRunningProcessName = segments[0]

    def setSelectedPackageName(self, name):
        self.currentSelectedApp = name

    def getSelectedPackageName(self):
        return self.currentSelectedApp

    @DeprecationWarning
    def getSelectedRunningProcessName(self):
        return self.currentSelectedRunningProcessName

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


