import re
import sqlite3


class SqlHelper:
    def __init__(self, name):
        self.name = name
        self.sqlObj = sqlite3.connect(name)

        self.table_ip = "ip"  # ip数据表名
        self.ip_column_address = "address"
        self.ip_column_port = "port"
        self.ip_column_usecounts = "counts"

        self.table_package = "package"  # 包名数据表名
        self.pkg_column_name = "name"

        self.cur = self.sqlObj.cursor()

    def createIpTable(self):
        if not self.sqlObj:
            return
        # self.cur.execute("CREATE TABLE IF NOT EXISTS {0}(id INTEGER PRIMARY KEY,{1} TEXT,{2} INTEGER)"
        #                  .format(self.table_ip, self.ipRow_Address, self.ipRow_UseCounts))
        self.cur.execute('CREATE TABLE IF NOT EXISTS {0}({1} TEXT, {2} TEXT, {3} INTEGER)'
                         .format(self.table_ip, self.ip_column_address,
                                 self.ip_column_port, self.ip_column_usecounts))
        self.__commint()

    def createPkgTable(self):
        # self.cur.execute("CREATE TABLE IF NOT EXISTS {0}(id INTEGER PRIMARY KEY,{1} TEXT)"
        #                  .format(self.table_package, self.pkg_column_name))
        self.cur.execute('CREATE TABLE IF NOT EXISTS {0}({1} TEXT)'
                         .format(self.table_package, self.pkg_column_name))
        self.__commint()

    def insertIpRow(self, ip="", port="5555", counts=0):
        ip_group = re.split(":", ip)
        host = ip
        _port = port
        if ip_group.__len__() == 2:
            host = ip_group[0]
            _port = ip_group[1]
        # 处理ip格式

        c = self.cur.execute('SELECT * FROM {0} WHERE {1}=\'{2}\''
                             .format(self.table_ip, self.ip_column_address, ip))
        for item in c:
            if item:
                if item[1] == _port:
                    return False, "此IP已存在,无需再次添加！"

        self.cur.execute('INSERT INTO {0} VALUES (\'{1}\',{2},{3})'
                         .format(self.table_ip, host, _port, counts))
        # 插入新数据

        self.__commint()
        return True, ''

    def insertPackageRow(self, package):
        self.cur.execute('INSERT INTO {0} VALUES (\'{1}\')'
                         .format(self.table_package, package))
        self.__commint()

    def updateData(self, newIp, idx=0):
        self.cur.execute('UPDATE {0} SET ip={1} WHERE id={2}'
                         .format(self.table_ip, newIp, idx))
        self.__commint()

    def queryData(self, column='*', tableName='default'):
        return self.cur.execute('SELECT {0} FROM {1}'
                                .format(column, tableName))

    def __commint(self):
        self.sqlObj.commit()

    def closeAll(self):
        self.cur.close()
        self.sqlObj.close()
