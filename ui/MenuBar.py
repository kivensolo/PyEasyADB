from PyQt5 import QtCore
from PyQt5.QtGui import QIcon
from PyQt5.QtWidgets import QAction, qApp

from logcat.log import z_logger


class Controller(object):
    def setupUi(self, parent):
        """
       通过QAction进行顶部菜单及菜单行为初始化
       QAction可以操作菜单栏,工具栏,或自定义键盘快捷键
       :parent: mainWindow
       """
        menubar = parent.menuBar()
        menubar.setGeometry(QtCore.QRect(0, 0, 705, 23))
        menubar.setObjectName("menubar")
        _translate = QtCore.QCoreApplication.translate

        z_logger.debug('initMenuBar :: actions')

        # menu_actions = [act_close, act_exit]
        menus = [
            _translate("Menu_Main", "菜单"),
            _translate("Menu_Edit", "编辑"),
            _translate("Menu_About", "关于")
        ]

        # 【菜单栏】
        #   新建连接
        new_connect = QAction(QIcon('./res/icons/add_new.png'), '&NewConnect', parent)
        new_connect.setObjectName("new_connect")
        new_connect.setShortcut('Ctrl+N')  # 自定义快捷键
        new_connect.setText(_translate("MainWindow", "连接新设备"))
        new_connect.triggered.connect(parent.show_new_device_dialog)
        #   关闭
        act_close = QAction(QIcon('./res/icons/close.png'), '&close', parent)
        act_close.setObjectName("act_close")
        act_close.setText(_translate("MainWindow", "Close"))
        #   退出应用
        act_exit = QAction(QIcon('./res/icons/app_quit.png'), '&Exit', parent)
        act_exit.setObjectName("exit_app")
        act_exit.setShortcut('Ctrl+Q')  # 自定义快捷键
        act_exit.setStatusTip('Exit application')  # 自定义提示
        act_exit.triggered.connect(qApp.quit)  # 建立信号连接槽
        act_exit.setText(_translate("MainWindow", "退出"))
        # act_close.setText("退出")

        # 【关于】
        logAction = QAction(parent)
        logAction.setObjectName("act_log")
        logAction.setText(_translate("act_log", "打开日志目录"))
        logAction.triggered.connect(parent.show_new_device_dialog)

        aboutAction = QAction(QIcon('./res/icons/add_new.png'), '&About', parent)
        aboutAction.setObjectName("act_about")
        aboutAction.setText(_translate("act_about", "关于"))
        aboutAction.triggered.connect(parent.show_new_device_dialog)

        actions = {
            # 菜单1对应的action
            menus[0]: [new_connect, act_close, act_exit],
            # 菜单2对应的action
            menus[1]: [act_exit],
            menus[2]: [logAction, aboutAction]
        }

        z_logger.debug('initMenuBars')
        # 初始化菜单项
        for menu in menus:
            menu_item = menubar.addMenu(menu)
            acts = actions[menu]
            for action in acts:
                menu_item.addAction(action)
            menubar.addAction(menu_item.menuAction())
        # 将menu添加到menubar上
        parent.setMenuBar(menubar)
