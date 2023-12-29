from PyQt5 import QtCore
from PyQt5.QtCore import QDir, QUrl
from PyQt5.QtGui import QIcon, QDesktopServices
from PyQt5.QtWidgets import QAction, qApp, QMenu

from src.logcat.log import z_logger
import xml.dom.minidom

# UI模板配置文件路径
menus_ui_config_file_path = "./config/menus_ui.xml"


class MenuActions(object):
    def __init__(self):
        self.mainWindow = None

    def setupUi(self, parent):
        """
       通过QAction进行顶部菜单及菜单行为初始化
       QAction可以操作菜单栏,工具栏,或自定义键盘快捷键
       :parent: mainWindow
       """
        self.mainWindow = parent
        menubar = parent.menuBar()
        menubar.setGeometry(QtCore.QRect(0, 0, 705, 23))
        menubar.setObjectName("menubar")
        _translate = QtCore.QCoreApplication.translate

        z_logger.debug('initMenuBar :: actions')

        dom = xml.dom.minidom.parse(menus_ui_config_file_path)
        root = dom.documentElement
        menu_list = root.getElementsByTagName("menu")
        for menu in menu_list:
            _menuGroup: QMenu = menubar.addMenu(menu.getAttribute("name"))
            action_list = menu.getElementsByTagName("action")
            for action in action_list:
                _action: QAction = QAction(parent=parent)
                actionName = action.getAttribute("name")
                _action.setText(actionName)

                state = action.getAttribute("state")
                if state == "disable":
                    _action.setEnabled(False)

                attrs = action.getElementsByTagName("attr")
                for attr in attrs:
                    _key = attr.getAttribute('name')
                    _value = attr.firstChild.nodeValue
                    if _key == "shortcut":
                        _action.setShortcut(_value)
                    elif _key == "icon":
                        _action.setIcon(QIcon(_value))
                    elif _key == "action":
                        _action.triggered.connect(lambda checked, cmd=_value: self.onActionClicked(cmd))
                _menuGroup.addAction(_action)
            menubar.addAction(_menuGroup.menuAction())
        parent.setMenuBar(menubar)

    def onActionClicked(self, cmd):
        if cmd == "m_connect_new_device":
            self.mainWindow.show_new_device_dialog()
        elif cmd == "m_close_app":
            qApp.quit()
        elif cmd == "m_open_log_page":
            self.open_log_folder()
        else:
            z_logger.error(f"该命令还未实现:{cmd}")

    @staticmethod
    def open_log_folder():
        # 获取当前工作目录
        current_dir = QDir.currentPath()
        # 使用系统资源管理器打开文件夹
        QDesktopServices.openUrl(QUrl.fromLocalFile(f"{current_dir}/data/logs"))
