from logcat.log import z_logger
from ui.sql import DBManager
from utils.ADBTools import ADBTools


class PackageManager:
    """
    全局信息管理的单例类
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

    def setSelectedPackage(self, pkgName):
        z_logger.debug("Set selected package:" + pkgName)
        self.currentSelectedPkg = pkgName

    def getCurrentSelectedPackage(self):
        return self.currentSelectedPkg

    def isDbReady(self):
        return self.dbManager

    def exec(self, sql):
        return self.dbManager.exec_sql(sql)

    def query(self, column='*', table_name='default'):
        return self.dbManager.queryData(column, table_name)

    def insert(self, pkgName):
        return self.dbManager.insertPackageRow(pkgName)

    def queryDeviceInfo(self, ip):
        return self.dbManager.get_device_prop_info(ip)

    def updateDeviceInfo(self, info, ip):
        return self.dbManager.update_device_prop(info, ip)


