from PyQt5.QtCore import pyqtSignal, QEvent
from PyQt5.QtWidgets import QWidget, QLabel, QToolButton, QHBoxLayout

from src.logcat.log import z_logger
from utils.UITools import IconTool


class ComboBoxItem(QWidget):
    """
    自定义ComboBox：
    https://zhuanlan.zhihu.com/p/36691866
    # QSS 使用
    # https://blog.csdn.net/bigtree_mfc/article/details/114091031?ops_request_misc=&request_id=&biz_id=102&utm_term=qcombobox%20%E5%88%A0%E9%99%A4%E6%8C%89%E9%92%AE&utm_medium=distribute.pc_search_result.none-task-blog-2~all~sobaiduweb~default-8-114091031.142^v5^pc_search_result_control_group,143^v6^register&spm=1018.2226.3001.4187
    """
    closeSignal = pyqtSignal(str)
    chooseSignal = pyqtSignal(str)

    def __init__(self, str_data):
        super().__init__()
        self.data = str_data
        self. initUi()

    def initUi(self):
        item_data_label = QLabel(self.data, self)

        self.bt_close = QToolButton(self)
        self.bt_close.setIcon(IconTool.buildQIcon("logo.png"))
        self.bt_close.setAutoRaise(True)

        # 布局设置为简单的横向布局
        root_layout = QHBoxLayout()
        root_layout.addWidget(item_data_label)
        root_layout.addWidget(self.bt_close)
        root_layout.minimumHeightForWidth(20)
        self.setLayout(root_layout)

        self.bt_close.installEventFilter(self)
        self.installEventFilter(self)

    def eventFilter(self, object, event):
        z_logger.debug(event.type())
        if object is self:
            if event.type() == QEvent.Enter:
                self.setStyleSheet("QWidget{color:white}")
            elif event.type() == QEvent.Leave:
                self.setStyleSheet("QWidget{color:black}")
            elif event.type() == QEvent.MouseButtonPress:
                self.chooseSignal.emit(self.data)
                pass
                # self.setStyleSheet("QWidget{color:black}")
        elif object is self.bt_close:
            if event.type() == QEvent.MouseButtonPress:
                self.closeSignal.emit(self.data)  # Close事件分发
        return QWidget.eventFilter(self, object, event)

