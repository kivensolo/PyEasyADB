import re
import sqlite3
from sqlite3 import Cursor

from config.settings import DB_NAME
from logcat.log import z_logger


class DBManager:
    """
    应用程序数据库管理类
    """
    TABLE_DEVICE = "device"
    TABLE_PACKAGE = "package"
    COLUMN_NAME = "name"

    def __init__(self):
        try:
            conn = sqlite3.connect(DB_NAME)
            cursor = conn.cursor()
            cursor.execute(
                'CREATE TABLE IF NOT EXISTS {0} '
                '(ip varchar(20) primary key,'
                'port varchar(10) default \'0\','
                'active binary(1) default 0)'.format(DBManager.TABLE_DEVICE))
            cursor.execute('CREATE TABLE IF NOT EXISTS {0} (name varchar(50) primary key)'.format(DBManager.TABLE_PACKAGE))
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
            print(e)
        finally:
            cursor.close()
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
        try:
            conn = sqlite3.connect(DB_NAME)
            cursor: Cursor = conn.cursor()
            cursor.execute(sql)
            conn.commit()
            result = cursor.fetchall()
        except Exception:
            return []
        finally:
            cursor.close()
            conn.close()
        return result

    def insertPackageRow(self, package):
        self.exec_sql('INSERT INTO {0} VALUES (\'{1}\')'.format(DBManager.TABLE_PACKAGE, package))

    def update_ip_data(self, newIp, idx=0):
        sql = 'UPDATE {0} SET ip={1} WHERE id={2}'.\
            format(DBManager.TABLE_DEVICE, newIp, idx)
        self.exec_sql(sql)

    def queryData(self, column='*', table_name='default'):
        sql = 'SELECT {0} FROM {1}'.format(column, table_name)
        return self.exec_sql(sql)

    def get_all_device(self):
        """
        从数据库中获取所有设备信息
        :return: 数据List集合
        """
        sql = 'select * from %s' % DBManager.TABLE_DEVICE
        return self.exec_sql(sql)

    def add_device_to_db(self, ip="", port="5555", active=0):
        """
        往设备信息表中插入新数据
        :param ip:      ip数据
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
            conn = sqlite3.connect(DB_NAME)
            cursor = conn.cursor()
            # 同一ip有多条端口数据  优化，改为一条数据
            sql_cmd = 'SELECT * FROM device WHERE ip =\'{0}\''.format(ip)
            result = cursor.execute(sql_cmd)
            for item in result:
                if item and item[1] == _port:
                    return False, "此设备已有记录,无需再次添加！"

            # cursor.execute("delete from device where ip = \'" + ip + "\'")
            cursor.execute('INSERT INTO device VALUES (\'{0}\',{1}, {2})'.format(host, _port, active))
            conn.commit()
            return True, '设备已入库'
        except Exception as e:
            print(e)
        finally:
            cursor.close()
            conn.close()

    def remove_device_from_db(self, ip="", port="5555"):
        z_logger.debug("remove_device_from_db :" + ip)
        ip_group = re.split(":", ip)
        host = ip
        _port = port
        if ip_group.__len__() == 2:
            host = ip_group[0]
            _port = ip_group[1]

        try:
            conn = sqlite3.connect(DB_NAME)
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
            conn = sqlite3.connect(DB_NAME)
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
