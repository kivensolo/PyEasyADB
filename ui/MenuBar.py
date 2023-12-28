from PyQt5 import QtCore
from PyQt5.QtGui import QIcon
from PyQt5.QtWidgets import QAction, qApp, QMenu

from logcat.log import z_logger
import xml.dom.minidom

# UI模板配置文件路径
menus_ui_config_file_path = "./config/menus_ui.xml"


class Controller(object):
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
        else:
            z_logger.error(f"该命令还未实现:{cmd}")
