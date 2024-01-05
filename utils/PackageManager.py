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
        self.currentSelectedPkg = ""
        self.dbManager = DBManager()
        self.adbTools = ADBTools()

    def setSelectedPackage(self, process_info):
        z_logger.debug("Set selected process:" + process_info)
        segments = str(process_info).split("(")
        self.currentSelectedPkg = segments[0]

    def getCurrentSelectedPackage(self):
        return self.currentSelectedPkg

    def isDbReady(self):
        return self.dbManager

    def exec(self, sql):
        return self.dbManager.exec_sql(sql)

    def query(self, column='*', table_name='default'):
        return self.dbManager.queryData(column, table_name)

    def addPackage(self, pkgName):
        return self.dbManager.addPackageToDB(pkgName)

    def isPackageExist(self, pkgName):
        package_info = self.dbManager.getAppPackageByName(pkgName)
        return len(package_info) != 0

    def queryDeviceInfo(self, ip):
        return self.dbManager.get_device_prop_info(ip)

    def updateDeviceInfo(self, info, ip):
        return self.dbManager.update_device_prop(info, ip)

    def updateDeviceAlias(self, ip, alias):
        return self.dbManager.update_device_alias(ip, alias)


