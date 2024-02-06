import sys
import xml.dom.minidom

from PyQt5 import QtCore, QtGui, QtWidgets
from PyQt5.QtCore import Qt, pyqtSlot, QSize, QDateTime, QTimer, pyqtSignal
from PyQt5.QtWidgets import QWidget, QApplication, QFileDialog, QMessageBox

from src import MainWindow, settings
from src.logcat.log import z_logger
from src.widget.CustomWidgets import DeleteableComboBox
from src.widget.Dialogs import installApkDialog, screen_record_dialog, TextInputDialog
from utils.ADBTools import ActionCmdParams
from utils.Tools import getWRYHFontStyle, getSongFontStyle, getSimpleFontStyle


class CommonFunctionalWidget(QWidget):
    """
    中间内容的主要布局
    """

    def __init__(self, parent: MainWindow):
        super().__init__()
        self.parent = parent
        self.setAttribute(Qt.WA_StyledBackground)
        self._init_convenient_area()

    # 新版布局逻辑
    def _init_convenient_area(self):
        convenient_area = Ui_ConvenientArea(self.parent)
        convenient_area.setUpUi(self)


# UI模板配置文件路径
template_ui_config_file_path = "./config/function_templates.xml"
# 每个ui模板一行的元素个数
every_row_size = 5


class Ui_ConvenientArea(object):
    edittext_changed = pyqtSignal([])

    def __init__(self, mainWidow):
        self.mainWindow = mainWidow
        self.timer = QTimer()
        self.timer.setSingleShot(True)
        self.timer.timeout.connect(self.record_text_data)

    def _initCustomAppActionArea(self, parentLayout):
        """
        初始化自定义行为区域(不是动态布局)
        :param parentLayout: QVBoxLayout of scrollArea
        :return:
        """
        self.groupBox = QtWidgets.QGroupBox(self.scrollAreaWidgetContents)
        self.groupBox.setFont(getSimpleFontStyle())
        self.groupBox.setObjectName("app_custom_action_group")
        self.group_vertical_layout = QtWidgets.QVBoxLayout(self.groupBox)
        self.group_vertical_layout.setObjectName("group_vertical_layout")
        self.groupBox.setTitle("应用参数配置")
        self.groupBox.setStyleSheet("QGroupBox { background-color:rgb(255,255,255);"
                                    "font-weight: bold; } ")
        # ==== 包名选择区域
        self.package_layout = QtWidgets.QHBoxLayout()
        self.package_layout.setObjectName("package_layout")
        self.label_app = QtWidgets.QLabel(self.groupBox)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.label_app.sizePolicy().hasHeightForWidth())
        self.label_app.setSizePolicy(sizePolicy)
        self.label_app.setObjectName("label_app")
        self.label_app.setText("package:")
        self.label_app.setFont(getWRYHFontStyle())
        self.package_layout.addWidget(self.label_app)
        # 自定义QComboBox
        self.packagesCombobox = DeleteableComboBox()
        self.packagesCombobox.setMainWinodw(self.mainWindow)
        self.packagesCombobox.setObjectName("custom_packages")
        pkg_local_data = self.mainWindow.pkgManager.query(table_name="package")
        self.packagesCombobox.initData(pkg_local_data)
        self.package_layout.addWidget(self.packagesCombobox)

        spacerItem1 = QtWidgets.QSpacerItem(40, 20, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Minimum)
        self.package_layout.addItem(spacerItem1)
        self.package_layout.setStretch(0, 1)
        self.package_layout.setStretch(1, 1)
        self.package_layout.setStretch(2, 2)
        self.group_vertical_layout.addLayout(self.package_layout)

        # ==== 类名
        self.classPathEditText: QtWidgets.QLineEdit = self.addCustomEditRow(
            settings.key_app_activity_classpath, "activity:",
            "目标activity的完整路径,如:com.example.myapp.MainActivity")
        self.actionEditText: QtWidgets.QLineEdit = self.addCustomEditRow(
            settings.key_app_action, "action :", "用于启动activity或广播发送")
        self.extendParamsEditText: QtWidgets.QTextEdit = self.addCustomEditRow(
            settings.key_app_extparams, "extend:", "扩展参数，如:--es \"key1\" \"value1\"", True)
        parentLayout.addWidget(self.groupBox)

    def _getCurrentSelectedPackage(self):
        """
        获取当前packagesCombobox所选应用名称
        :return:
        """
        return self.packagesCombobox.currentText()

    def onEditTextValueChanged(self, objName, text):
        # self.timer.start(1000)  # 重置计时器，设置超时时间为1000毫秒（1秒）
        self.mainWindow.settings.setValue(objName, text)
        # if self.timer.isActive():
        #     self.timer.stop()
        # else:
        #     self.timer.start(2000)
        # self.settings.setValue(key, value)-

    def record_text_data(self):
        pass

    def addCustomEditRow(self, objName, labelText, holderText, isTextEdit=False):
        """
        添加一行文字+editText的布局
        :param objName:
        :param labelText:
        :param holderText:
        :param isTextEdit:
        :return:
        """
        h_layout = QtWidgets.QHBoxLayout()
        h_layout.setObjectName("labelText")
        labelView = QtWidgets.QLabel(self.groupBox)
        labelView.setText(labelText)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Fixed, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(labelView.sizePolicy().hasHeightForWidth())
        labelView.setSizePolicy(sizePolicy)
        labelView.setFont(getWRYHFontStyle())
        h_layout.addWidget(labelView)

        if isTextEdit:
            lineEdit = QtWidgets.QTextEdit(self.groupBox)
            lineEdit.textChanged.connect(
                lambda: self.onEditTextValueChanged(lineEdit.objectName(), lineEdit.toPlainText()))
        else:
            lineEdit = QtWidgets.QLineEdit(self.groupBox)
            lineEdit.textChanged.connect(lambda: self.onEditTextValueChanged(lineEdit.objectName(), lineEdit.text()))
        lineEdit.setObjectName(objName)
        lineEdit.setPlaceholderText(holderText)
        lineEdit.setFont(getSongFontStyle(10))
        _cachedText = self.mainWindow.settings.value(objName)
        if _cachedText != "" and _cachedText is not None:
            lineEdit.setText(_cachedText)
        h_layout.addWidget(lineEdit)

        spacerItem = QtWidgets.QSpacerItem(40, 20, QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Minimum)
        h_layout.addItem(spacerItem)

        h_layout.setStretch(0, 1)
        h_layout.setStretch(1, 4)
        h_layout.setStretch(2, 1)
        self.group_vertical_layout.addLayout(h_layout)
        return lineEdit

    def setUpUi(self, ConvenientArea):
        ConvenientArea.setObjectName("ConvenientArea")

        # 此UI区域的根布局
        self.ui_root_vlayout = QtWidgets.QVBoxLayout(ConvenientArea)
        self.ui_root_vlayout.setObjectName("functionVLayout")
        self.scrollArea = QtWidgets.QScrollArea(ConvenientArea)
        sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Expanding, QtWidgets.QSizePolicy.Preferred)
        sizePolicy.setHorizontalStretch(0)
        sizePolicy.setVerticalStretch(0)
        sizePolicy.setHeightForWidth(self.scrollArea.sizePolicy().hasHeightForWidth())
        self.scrollArea.setSizePolicy(sizePolicy)
        self.scrollArea.setStyleSheet("""
            QScrollArea{
                background:transparent;
                border: 1px solid black;
            };
        """)
        self.scrollArea.setWidgetResizable(True)
        self.scrollArea.setObjectName("scrollArea")
        # 这个widget是滚动view的第一个Child
        self.scrollAreaWidgetContents = QtWidgets.QWidget()
        self.scrollAreaWidgetContents.setGeometry(QtCore.QRect(0, 0, 272, 318))
        self.scrollAreaWidgetContents.setObjectName("scrollAreaWidgetContents")
        self.scrollAreaWidgetContents.setStyleSheet("""
            QWidget#scrollAreaWidgetContents{
                background:transparent;
            }
        """)
        # 滚动区域为垂直layout
        self.vlayout_of_scrollarea = QtWidgets.QVBoxLayout(self.scrollAreaWidgetContents)
        self.vlayout_of_scrollarea.setObjectName("scroll_area_layout")
        # self.vlayout_of_scrollarea.setSpacing(0)

        # 【自定义区域】将groupBox加入垂直滚动布局中
        self._initCustomAppActionArea(self.vlayout_of_scrollarea)

        # 【动态布局区域】
        self._setUpUIDynamic()

        # 给滚动区域设置Qwidgets
        self.scrollArea.setWidget(self.scrollAreaWidgetContents)
        self.ui_root_vlayout.addWidget(self.scrollArea)

    def _setUpUIDynamic(self):
        # 【setUpUiDynamic】
        # if __name__ == "__main__":
        #     template_ui_config_file_path = "../../config/function_templates.xml"
        # 动态设置groupView
        dom = xml.dom.minidom.parse(template_ui_config_file_path)
        root = dom.documentElement
        template_list = root.getElementsByTagName("template")
        for template in template_list:
            template_name = template.getAttribute('name')
            template_layout = template.getAttribute('layout')
            # 模板区域数量检查，创建分组的GroupBox
            _groupBox = QtWidgets.QGroupBox(self.scrollAreaWidgetContents)
            _groupBox.setStyleSheet("QGroupBox { font-weight: bold; } ")
            sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
            sizePolicy.setHorizontalStretch(0)
            sizePolicy.setVerticalStretch(0)
            sizePolicy.setHeightForWidth(_groupBox.sizePolicy().hasHeightForWidth())
            _groupBox.setSizePolicy(sizePolicy)
            _groupBox.setCheckable(False)
            _groupBox.setObjectName(template_name)
            _groupBox.setTitle(template_name)
            _groupBox.setFont(getSimpleFontStyle())

            item_list = template.getElementsByTagName("item")

            if template_layout == "grid":
                # grid模式的模板布局
                gridLayout = QtWidgets.QGridLayout(_groupBox)
                gridLayout.setObjectName("template_" + template_name)

                # 每个模板区域有几个行为控件
                current_template_item_counts = len(item_list)
                for index in range(current_template_item_counts):
                    item = item_list[index]
                    rowIndex = int(index / every_row_size)
                    columnIndex = index % every_row_size

                    # 初始化每一个tool按钮
                    item_tool_button = QtWidgets.QToolButton(_groupBox)
                    item_tool_button.setObjectName("{0}_item_{1}{2}".format(template_name, rowIndex, columnIndex))
                    item_tool_button.setAutoRaise(True)

                    item_state = item.getAttribute("state")
                    if item_state == "disable":  # 未开发功能设置为disable
                        item_tool_button.setEnabled(False)
                        item_tool_button.setToolTip("该功能未开发，敬请期待")

                    sizePolicy = QtWidgets.QSizePolicy(QtWidgets.QSizePolicy.Preferred, QtWidgets.QSizePolicy.Preferred)
                    sizePolicy.setHorizontalStretch(0)
                    sizePolicy.setVerticalStretch(0)
                    item_tool_button.setSizePolicy(sizePolicy)
                    attrs = item.getElementsByTagName("attr")
                    actionParams = ActionCmdParams()
                    for attr in attrs:
                        if attr.firstChild is None:
                            continue
                        _key = attr.getAttribute('name')
                        _value = attr.firstChild.nodeValue
                        if _key == "text":
                            font = QtGui.QFont()
                            font.setPointSize(10)
                            item_tool_button.setText(_value)
                            item_tool_button.setFont(font)
                        elif _key == "icon":
                            item_tool_button.setIcon(QtGui.QIcon(_value))
                            item_tool_button.setIconSize(QSize(56, 56))
                            item_tool_button.setToolButtonStyle(Qt.ToolButtonStyle.ToolButtonTextUnderIcon)
                        elif _key == "cmd":
                            shellValue = attr.getAttribute("shell")
                            actionParams.isShellMode = (shellValue.lower() != "false")
                            actionParams.cmd = _value
                        elif _key == "act":
                            shellValue = attr.getAttribute("shell")
                            actionParams.isShellMode = (shellValue.lower() != "false")
                            actionParams.custom_action = _value
                    """
                    https://blog.csdn.net/PixelNovaO/article/details/132727483
                    每次迭代时创建一个新的闭包，以便为每个按钮创建一个独立的事件处理器。并将自定义对象作为参数传递。
                    使用了lambda 函数来创建一个新的闭包，以捕获当前的按钮对象和自定义对象。这样，每个按钮的事件处理器都会独立地处理各自的对象。
                    """
                    item_tool_button.clicked.connect(
                        lambda checked, params=actionParams: self.onFunctionItemClicked(params))

                    gridLayout.addWidget(item_tool_button, rowIndex, columnIndex, 1, 1)

            # 若第一行未满，则进行填充, 使UI按照网格对齐
            if current_template_item_counts < every_row_size and \
                    current_template_item_counts % every_row_size != 0:
                last_row_index = int(current_template_item_counts / every_row_size)
                inflate_item_start_index = current_template_item_counts % every_row_size
                inflate_counts = every_row_size - inflate_item_start_index
                for i in range(inflate_counts):
                    inflate_column_index = i + inflate_item_start_index
                    label = QtWidgets.QLabel(_groupBox)
                    label.setObjectName("inflate_0%s".format(inflate_column_index))
                    label.setObjectName("item_%s_%s".format(last_row_index, inflate_column_index))
                    gridLayout.addWidget(label, 0, inflate_column_index, 1, 1)

            # 将groupBox加入垂直滚动布局中
            self.vlayout_of_scrollarea.addWidget(_groupBox)
        # 最底部添加"弹簧"
        scroll_bottom_spacer_item = QtWidgets.QSpacerItem(20, 20, QtWidgets.QSizePolicy.Minimum,
                                                          QtWidgets.QSizePolicy.Expanding)
        self.vlayout_of_scrollarea.addItem(scroll_bottom_spacer_item)

    @pyqtSlot()
    def onFunctionItemClicked(self, actionParams: ActionCmdParams):
        """
        功能按钮被点击的回调函数
        :param actionParams:
        :return:
        """
        _action = actionParams.custom_action
        _cmd = actionParams.cmd
        if _action != "":
            self.dealCustomAction(_action, actionParams)
        else:
            if "uninstall" in _cmd:
                reply = QMessageBox.question(
                    self.mainWindow, '提示', f"确认卸载以下应用:\n {self._getCurrentSelectedPackage()}",
                                             QMessageBox.Yes | QMessageBox.No,
                                             QMessageBox.No)
                if reply == QMessageBox.Yes:
                    self.runAdbCMD(actionParams)
            else:
                self.runAdbCMD(actionParams)

    def dealCustomAction(self, custom_act, actionParams: ActionCmdParams):
        """
        处理自定义行为
        :param custom_act:
        :param actionParams:
        :return:
        """
        if custom_act == "m_show_install_app_dialog":
            if not self.hasSelectedPackage():
                return
            install_apk_dialog = installApkDialog(self.mainWindow)
            install_apk_dialog.setWindowModality(Qt.ApplicationModal)
            install_apk_dialog.exec()
        elif custom_act == "m_screenshot":
            # 屏幕截图
            self.action_save_screen_shoot()
        elif custom_act == "m_screen_record":
            # 屏幕录制
            _record_dialog = screen_record_dialog(self.mainWindow)
            _record_dialog.setWindowModality(Qt.ApplicationModal)
            _record_dialog.exec()
        elif custom_act == "m_start_app":
            # 启动应用
            self.start_app(actionParams)
        elif custom_act == "m_restart_app":
            # 重启应用
            self.restart_app()
        elif custom_act == "m_input_text":
            # 文本输入
            _text_input_dialog = TextInputDialog(self.mainWindow)
            _text_input_dialog.setWindowModality(Qt.ApplicationModal)
            _text_input_dialog.exec()
        elif custom_act == "m_send_broadcast":
            # 广播发送
            self.send_broadcast(actionParams)
        elif custom_act == "m_query_contentprovider":
            # 查询ContentProvider
            self.query_content_provider()
        else:
            z_logger.error(f"不支持的自定义行为:{custom_act}")

    def build_am_cmd(self, name):
        """
        构建am的执行命令，支持从自定义区域获取自定义的数据进行命令拼接
        :param name: am指令名称，如start\boradercast\kill 等
        :return:
        """
        _cmd = f"am {name}"
        _package_name = self._getCurrentSelectedPackage()
        _act = self.actionEditText.text()
        _class_path = self.classPathEditText.text()
        if _act == "" and _class_path == "":
            _cmd += f" {_package_name}"
        else:
            if _act != "":
                _cmd += f" -a {_act}"
            if _class_path != "":
                _cmd += f" -n {_package_name}/{_class_path}"
        _extParams = self.extendParamsEditText.toPlainText()
        if _extParams != "":
            _cmd += f" {_extParams}"
        return _cmd

    def send_broadcast(self, actionParams):
        _cmd = self.build_am_cmd("broadcast")
        actionParams.cmd = _cmd
        self.runAdbCMD(actionParams)

    def query_content_provider(self):
        uri_path = self.extendParamsEditText.toPlainText()
        if "content://" not in uri_path:
            # z_logger.info(r"请在[extend]扩展编辑框中，正确填入需要查询的uri! 格式要求: content://<authority>/<path>")
            # 使用实体编码解决<>被识别错误的问题
            z_logger.error(
                "请在[extend]扩展编辑框中，正确填入需要查询的uri! 格式要求: content://&lt;authority&gt;/&lt;path&gt;")
            return
        params = ActionCmdParams(needPackage=False)
        params.cmd = f"content query --uri {uri_path}"
        self.runAdbCMD(params)

    def start_app(self, actionParams):
        """
        启动应用
        :param actionParams: ActionCmdParams
        :return: 执行的adb命令（不包含adb -s <ip> shell前缀)
        """
        _cmd = self.build_am_cmd("start")
        actionParams.cmd = _cmd
        self.runAdbCMD(actionParams)

    def restart_app(self):
        """
        重启应用
        """
        cmd_1 = ActionCmdParams()
        cmd_1.target_device_ip = self.mainWindow.current_device_addr
        cmd_1.needDstPkg = True
        cmd_1.target_app = self._getCurrentSelectedPackage()
        cmd_1.cmd = "am force-stop {0}"

        cmd_2 = ActionCmdParams()
        cmd_2.target_device_ip = self.mainWindow.current_device_addr
        cmd_2.needDstPkg = False
        cmd_2.cmd = "sleep 1"

        cmd_3 = ActionCmdParams()
        cmd_3.target_device_ip = self.mainWindow.current_device_addr
        cmd_3.needDstPkg = True
        cmd_3.target_app = self._getCurrentSelectedPackage()
        cmd_3.cmd = self.build_am_cmd("start")
        cmds = [cmd_1, cmd_2, cmd_3]

        self.mainWindow.adbTools.async_exec_adb_cmd(cmds)

    def action_save_screen_shoot(self):
        """
        执行屏幕截图，并保存至本地
        :return:
        """
        if not self.hasSelectedPackage():
            return
        z_logger.info_with_stamp("Screenshot saving..........")
        chooseDialog = QFileDialog
        default_file_name = QDateTime.currentDateTime().toString("yyyyMMdd_hhmmss")
        savePath = chooseDialog.getSaveFileName(
            self.mainWindow, "保存截图", f"screenshot_{default_file_name}.png",
            "Image Files (*.png)")[0]
        if savePath:
            self.mainWindow.adbTools.get_screen_shoot(
                self.mainWindow.current_device_addr,
                savePath
            )
        else:
            z_logger.info_with_stamp("Cancle screenshot.")

    def _on_screen_shoot_finished(self, result):
        z_logger.debug(result)

    def runAdbCMD(self, actionParams: ActionCmdParams):
        self.mainWindow.runAdbCMD(actionParams)

    def hasSelectedPackage(self):
        if len(self.mainWindow.connected_device_list) == 0:
            z_logger.error("请先连接设备!!!")
            return False
        return True


if __name__ == "__main__":
    app = QApplication(sys.argv)
    mainWin = CommonFunctionalWidget(None)
    mainWin.show()
    sys.exit(app.exec_())
