
from PyQt5.QtWidgets import (QWidget, QProgressBar,
    QPushButton, QApplication)
from PyQt5.QtCore import QBasicTimer
import sys


class Example(QWidget):
    """
    This example shows a QProgressBar widget.
    """
    def __init__(self):
        super().__init__()
        self.initUI()

    def initUI(self):
        # 创建一个水平的进度条和一个按钮，这个按钮控制进度条的开始和停止
        self.pbar = QProgressBar(self)
        self.pbar.setGeometry(30, 40, 200, 25)

        self.btn = QPushButton('Start', self)
        self.btn.move(40, 80)
        self.btn.clicked.connect(self.doAction)

        self.timer = QBasicTimer()
        self.step = 0

        self.setGeometry(300, 300, 280, 170)
        self.setWindowTitle('QProgressBar')
        self.show()

    def timerEvent(self, e):
        """
        每个QObject和由它继承而来的对象都有一个`timerEvent()`事件处理函数。
        为了触发事件，重载了这个方法。
        """
        if self.step >= 100:
            self.timer.stop()
            self.btn.setText('Finished')
            return
        self.step = self.step + 1
        self.pbar.setValue(self.step)

    def doAction(self):
        # 控制开始和停止
        if self.timer.isActive():
            self.timer.stop()
            self.btn.setText('Start')
        else:
            # start()方法加载一个时间事件。有两个参数：过期时间和事件接收者
            self.timer.start(100, self)
            self.btn.setText('Stop')


if __name__ == '__main__':
    app = QApplication(sys.argv)
    ex = Example()
    sys.exit(app.exec_())