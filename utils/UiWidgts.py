from PyQt5.QtCore import *
from PyQt5.QtGui import *
from PyQt5.QtWidgets import *

"""
Qss示例:

# /*这里是通用设置，所有按钮都有效，不过后面的可以覆盖这个*/
# QPushButton {
#     border: none; /*去掉边框*/
# }

# /*
# QPushButton#xxx
# 或者
# #xx
# 都表示通过设置的objectName来指定
# */
QPushButton#ToolButton {
    background-color: #00c3c3c3; /*背景颜色*/
    # border:1px solid #303030;
}
ToolButton:hover {
    background-color: #ff0909; /*鼠标悬停时背景颜色*/
}
# /*注意pressed一定要放在hover的后面，否则没有效果*/
#ToolButton:pressed {
    background-color: #ffcdd2; /*鼠标按下不放时背景颜色*/
}

#BlueButton {
    background-color: #2196f3;
    padding-top:8px; //文字向下移动
    text-align:left; 文字左对齐
    image:url(":/delete.png"); //加图标
    /*限制最小最大尺寸*/
    min-width: 96px;
    max-width: 96px;
    min-height: 96px;
    max-height: 96px;
    border-radius: 48px; /*圆形*/
    border-top-right-radius: 20px; /*右上角圆角*/
    border-bottom-left-radius: 20px; /*左下角圆角*/
}

#QPushButton:disabled { /*设置禁用时按钮的样式*/ }

/*根据文字内容来区分按钮,同理还可以根据其它属性来区分*/
QPushButton[text="purple button"] {
    color: white; /*文字颜色*/
    background-color: #9c27b0;
}
"""


class AppPushButton(QPushButton):
    """
    App公共的PushButton
    https://blog.csdn.net/sazass/article/details/117018947
    """
    def __init__(self, text="", slotFun=None):
        super().__init__(text, objectName="ToolButton")

        self.font = QFont()
        self.font.setPixelSize(24)  # 字体大小

        self.setStyleSheet("""
            QPushButton {
                background-color: transparent;
                border: none;
                padding:1px,1px,1px,1px;
                margin:3px,1px,3px,1px;
            }
            QPushButton:hover {
                background-color: #b3d7f3;
                border: 1px solid #1e88dc;
            }
            QPushButton:pressed {
                background-color: #80bceb;
                border: 1px solid #2d8fdc;
            }
        """)
        # 如果用QSS设置font-size:18px; 在不同分辨率上字体会变化。
        if slotFun is not None:
            self.clicked.connect(slotFun)


class SwitchBtn(QWidget):
    # 信号
    checkedChanged = pyqtSignal(bool)

    def __init__(self, parent=None):
        super(QWidget, self).__init__(parent)

        self.checked = False
        self.bgColorOff = QColor(255, 255, 255)
        self.bgColorOn = QColor(0, 0, 0)

        self.sliderColorOff = QColor(100, 100, 100)
        self.sliderColorOn = QColor(100, 184, 255)

        self.textColorOff = QColor(143, 143, 143)
        self.textColorOn = QColor(255, 255, 255)

        self.textOff = "OFF"
        self.textOn = "ON"

        self.space = 2
        self.rectRadius = 5

        self.step = self.width() / 50
        self.startX = 0
        self.endX = 0

        self.timer = QTimer(self)  # 初始化一个定时器
        self.timer.timeout.connect(self.updateValue)  # 计时结束调用operate()方法

        # self.timer.start(5)  # 设置计时间隔并启动

        self.setFont(QFont("Microsoft Yahei", 10))

        # self.resize(55,22)

    def updateValue(self):
        if self.checked:
            if self.startX < self.endX:
                self.startX = self.startX + self.step
            else:
                self.startX = self.endX
                self.timer.stop()
        else:
            if self.startX > self.endX:
                self.startX = self.startX - self.step
            else:
                self.startX = self.endX
                self.timer.stop()

        self.update()

    def mousePressEvent(self, event):
        self.checked = not self.checked
        # 发射信号
        self.checkedChanged.emit(self.checked)

        # 每次移动的步长为宽度的50分之一
        self.step = self.width() / 50
        # 状态切换改变后自动计算终点坐标
        if self.checked:
            self.endX = self.width() - self.height()
        else:
            self.endX = 0
        self.timer.start(5)

    def paintEvent(self, evt):
        # 绘制准备工作, 启用反锯齿
        painter = QPainter()

        painter.begin(self)

        painter.setRenderHint(QPainter.Antialiasing)

        # 绘制背景
        self.drawBg(evt, painter)
        # 绘制滑块
        self.drawSlider(evt, painter)
        # 绘制文字
        self.drawText(evt, painter)

        painter.end()

    def drawText(self, event, painter):
        painter.save()

        if self.checked:
            painter.setPen(self.textColorOn)
            painter.drawText(0, 0, self.width() / 2 + self.space * 2, self.height(), Qt.AlignCenter,
                             self.textOn)
        else:
            painter.setPen(self.textColorOff)
            painter.drawText(self.width() / 2, 0, self.width() / 2 - self.space, self.height(),
                             Qt.AlignCenter, self.textOff)

        painter.restore()

    def drawBg(self, event, painter):
        painter.save()
        painter.setPen(Qt.NoPen)

        if self.checked:
            painter.setBrush(self.bgColorOn)
        else:
            painter.setBrush(self.bgColorOff)

        rect = QRect(0, 0, self.width(), self.height())
        # 半径为高度的一半
        radius = rect.height() / 2
        # 圆的宽度为高度
        circleWidth = rect.height()

        path = QPainterPath()
        path.moveTo(radius, rect.left())
        path.arcTo(QRectF(rect.left(), rect.top(), circleWidth, circleWidth), 90, 180)
        path.lineTo(rect.width() - radius, rect.height())
        path.arcTo(QRectF(rect.width() - rect.height(), rect.top(), circleWidth, circleWidth), 270,
                   180)
        path.lineTo(radius, rect.top())

        painter.drawPath(path)
        painter.restore()

    def drawSlider(self, event, painter):
        painter.save()

        if self.checked:
            painter.setBrush(self.sliderColorOn)
        else:
            painter.setBrush(self.sliderColorOff)

        rect = QRect(0, 0, self.width(), self.height())
        sliderWidth = rect.height() - self.space * 2
        sliderRect = QRect(self.startX + self.space, self.space, sliderWidth, sliderWidth)
        painter.drawEllipse(sliderRect)

        painter.restore()
