import re
import sqlite3
from sqlite3 import Cursor

from src.settings import APP_DB_FILE
from src.logcat.log import z_logger


class DBManager:
    """
    python数据库操作文章
    https://www.zhihu.com/question/391210389/answer/2305057909
    """
    """
    应用程序数据库管理类
    """
    TABLE_DEVICE = "device"
    TABLE_PACKAGE = "package"
    COLUMN_NAME = "name"

    def __init__(self):
        conn = None
        cursor = None
        try:
            conn = sqlite3.connect(APP_DB_FILE)
            cursor = conn.cursor()
            cursor.execute(
                'CREATE TABLE IF NOT EXISTS {0} '
                '(ip VARCHAR(20) PRIMARY KEY,'
                'port VARCHAR(10) DEFAULT 0,'
                'alias VARCHAR(50) NOT NULL DEFAULT \'\','
                'device_info VARCHAR(50) NOT NULL DEFAULT \'10086\','
                'active binary(1) DEFAULT 0)'.format(DBManager.TABLE_DEVICE))
            cursor.execute(f"CREATE TABLE IF NOT EXISTS {DBManager.TABLE_PACKAGE} (name varchar(50) primary key)")
            # cursor.execute('create table if not exists '+DBManager.TABLE_HISTORY+
            #                '(id integer primary key autoincrement, '
            #                'name nvarchar(50) null,'
            #                'url nvarchar(512) null,'
            #                'date TIMESTAMP null,'
            #                'favorite binary(1) default 0)')
            # cursor.execute('create table if not exists '+DBManager.TABLE_FAVORITES+
            #                ' (id integer primary key autoincrement, '
            #                'name nvarchar(50) null,'
            #                'url nvarchar(512) null,'
            #                'date TIMESTAMP null,'
            #                'history_id integer not null,'
            #                'UNIQUE(history_id),'
            #                'FOREIGN KEY (history_id) REFERENCES history_query(id))')
            # cursor.execute('create table if not exists ' + DBManager.TABLE_CONFIGURATION +
            #                ' (id integer primary key autoincrement,'
            #                'config_key nvarchar(100) not null unique ,'
            #                'config_value nvarchar(512) null )')
        except Exception as e:
            print("数据库检查失败:" + e)
        finally:
            if cursor is not None:
                cursor.close()
            if conn is not None:
                conn.close()

        # self.conn = sqlite3.connect(database_name)

        # self.table_ip = "ip"  # ip数据表名
        # self.ip_column_address = "address"
        # self.ip_column_port = "port"
        # self.ip_column_usecounts = "counts"
        #
        # self.table_package = "package"  # 包名数据表名
        # self.pkg_column_name = "name"
        #
        # self.cursor = self.conn.cursor()

    def exec_sql(self, sql):
        conn = None
        cursor = None
        try:
            conn = sqlite3.connect(APP_DB_FILE)
            cursor: Cursor = conn.cursor()
            cursor.execute(sql)
            conn.commit()
            if ("insert" in sql.lower()) or ("delete" in sql.lower()):
                # 使用cursor.rowcount属性获取受影响的行数。如果大于0，则表示插入成功
                result = (cursor.rowcount > 0)
            else:
                # 使用cursor.fetchall()方法获取查询结果。如果返回的结果不为空，则表示查询成功。
                result = cursor.fetchall()
        except Exception as e:
            return [False, str(e)]
        finally:
            if cursor is not None:
                cursor.close()
            if conn is not None:
                conn.close()
        return [True, result]

    def addPackageToDB(self, package):
        return self.exec_sql(f"INSERT INTO {DBManager.TABLE_PACKAGE} (name) VALUES (\'{package}\')")

    def getAppPackageByName(self, pkg_name):
        return self.exec_sql(f"SELECT * FROM {DBManager.TABLE_PACKAGE} WHERE name=\'{pkg_name}\'")

    def update_ip_data(self, newIp, idx=0):
        sql = f"UPDATE {DBManager.TABLE_DEVICE} SET ip={newIp} WHERE id={idx}"
        return self.exec_sql(sql)

    def update_device_prop(self, info, ip):
        sql = f"UPDATE {DBManager.TABLE_DEVICE} SET device_info=\'{info}\' WHERE ip=\'{ip}\'"
        return self.exec_sql(sql)

    def update_device_alias(self, ip, alias):
        sql = f"UPDATE {DBManager.TABLE_DEVICE} SET alias=\'{alias}\' WHERE ip=\'{ip}\'"
        return self.exec_sql(sql)

    def queryData(self, column='*', table_name='default'):
        """
        通用数据查询的API
        :param column:  列名称，默认选择所有
        :param table_name:  表名,默认default
        :return:
        """
        sql = f"SELECT {column} FROM {table_name}"
        return self.exec_sql(sql)[1]

    def get_all_device(self):
        """
        从数据库中获取所有设备信息
        :return: 数据List集合
        """
        sql = f"select * from {DBManager.TABLE_DEVICE}"
        return self.exec_sql(sql)[1]

    def add_device_to_db(self, ip="", port="0", active=0):
        """
        往设备信息表中插入新数据
        :param ip:      设备名称数据，最开始只考虑了ip，其实可能有纯字符串，纯数字
        :param port:    端口数据
        :param active:  是否激活状态(连接中)
        :return:
            Boolean: 状态
            String ：结果描述
        """
        ip_group = re.split(":", ip)
        host = ip
        _port = port
        if ip_group.__len__() == 2:
            host = ip_group[0]
            _port = ip_group[1]
        # 处理ip格式
        try:
            conn = sqlite3.connect(APP_DB_FILE)
            cursor = conn.cursor()
            # 同一ip有多条端口数据  优化，改为一条数据
            sql_cmd = f"SELECT * FROM device WHERE ip =\'{host}\'"
            result = cursor.execute(sql_cmd)
            for item in result:
                if item and item[1] == _port:
                    return False, "此设备已有记录,无需再次添加！"

            # cursor.execute("delete from device where ip = \'" + ip + "\'")
            cursor.execute(f"INSERT INTO device VALUES (\'{host}\',\'{_port}\', \'\', \'\', {active})")
            conn.commit()

            if _port:
                name = host + ":" + _port
            else:
                name = host
            return True, name
        except Exception as e:
            z_logger.error('设备入库失败, 请检查Sql语句:' + str(e))
            return False, "设备入库失败, 请检查Sql语句"
        finally:
            cursor.close()
            conn.close()

    @staticmethod
    def get_device_prop_info(addr):
        ip_group = re.split(":", addr)
        host = addr
        if len(ip_group) == 2:
            host = ip_group[0]
        sql_cmd = f"SELECT device_info FROM device WHERE ip =\'{host}\'"
        result, result = DBManager._query_data(sql_cmd)
        if result:
            return True, result[0]
        else:
            return False, ''

    @staticmethod
    def _query_data(sql):
        try:
            conn = sqlite3.connect(APP_DB_FILE)
            cursor = conn.cursor()
            cursor.execute(sql)
            result_list = cursor.fetchall()
            return True, result_list
        except Exception as e:
            z_logger.error("数据库查询异常." + str(e))
            return False, ['']
        finally:
            cursor.close()
            conn.close()

    @staticmethod
    def remove_device_from_db(ip="", port="5555"):
        z_logger.debug("remove_device_from_db :" + ip)
        ip_group = re.split(":", ip)
        host = ip
        _port = port
        if ip_group.__len__() == 2:
            host = ip_group[0]
            _port = ip_group[1]

        try:
            conn = sqlite3.connect(APP_DB_FILE)
            cursor = conn.cursor()
            # 删除设备的
            sql_cmd = 'DELETE FROM device WHERE ip =\'{0}\''.format(host)
            result = cursor.execute(sql_cmd)
            # for item in result:
            #     if item and item[1] == _port:
            #         return False, "此设备已有记录,无需再次添加！"
            conn.commit()
            return True, '设备已移除'
        except Exception as e:
            print(e)
        finally:
            cursor.close()
            conn.close()

    def change_device_state(self, ip='', isConnected=True):
        """
        改变设备连接状态
        :param ip:  目标设备ip
        :param isConnected:  是否连接
        :return:
        """
        try:
            conn = sqlite3.connect(APP_DB_FILE)
            cursor = conn.cursor()
            # 更新指定设备active字段
            sql_cmd = "UPDATE device SET active={0} WHERE ip={1}" .format(isConnected, ip)
            result = cursor.execute(sql_cmd)
            # for item in result:
            #     if item and item[1] == _port:
            #         return False, "此设备已有记录,无需再次添加！"
            conn.commit()
            return True, '设备状态已变更'
        except Exception as e:
            print(e)
        finally:
            cursor.close()
            conn.close()

    def __commint(self):
        self.conn.commit()

    def closeAll(self):
        self.cursor.close()
        self.conn.close()
