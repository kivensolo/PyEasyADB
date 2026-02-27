import os

from PyQt5.QtCore import Qt
from PyQt5.QtGui import QIcon, QPixmap, QPainter, QDragEnterEvent
from PyQt5.QtSvg import QSvgRenderer

from src.logcat.log import z_logger


class UiUtils(object):
    #  2.8K屏幕: 2880 * 1800
    windowWidth = 1920
    windowHeight = 1080
    itemHeight = 30
    # 基于1080P的缩放因子
    x_scale_factor = 1.0
    y_scale_factor = 1.0
    scale_factor = 1.0


    @staticmethod
    def init(qScreen):
        """
        初始化获取当前屏幕信息
        :param qScreen:
        :return:
        """
        screen_size = qScreen.size()  # 或 screen.geometry().size()
        UiUtils.windowWidth = screen_size.width()
        UiUtils.windowHeight = screen_size.height()
        UiUtils.x_scale_factor = UiUtils.windowWidth / 1920
        UiUtils.y_scale_factor = UiUtils.windowHeight / 1080
        dpi = qScreen.logicalDotsPerInch()
        scale_factor = dpi / 96.0
        z_logger.debug("PrimaryScreen info: size={0}x{1} dpi={2} scale={3}".format(
            UiUtils.windowWidth,
            UiUtils.windowHeight,
            dpi,
            scale_factor
        ))


    @staticmethod
    def setWindowWidth(width):
        UiUtils.windowWidth = width

    @staticmethod
    def setWindowHeight(height):
        UiUtils.windowHeight = height
        UiUtils.itemHeight = int(UiUtils.windowHeight / 36.0)

    @staticmethod
    def getWindowWidth():
        return UiUtils.windowWidth

    @staticmethod
    def getWindowHeight():
        return UiUtils.windowHeight

    @staticmethod
    def getItemHeight():
        return UiUtils.itemHeight

    @staticmethod
    def getScaleWidth(width):
        return int(UiUtils.x_scale_factor * width)

    @staticmethod
    def getScaleHeight(height):
        return int(UiUtils.y_scale_factor * height)

    @staticmethod
    def getScaleValue(value):
        return int(UiUtils.scale_factor * value)


class IconTool:
    def __init__(self):
        super().__init__()

    @staticmethod
    def buildQIcon(iconName, dir="img"):
        path = os.path.join(".", "res", dir, iconName)
        return QIcon(path)

    @staticmethod
    def getIconFromSVG(name):
        svg_path = os.path.join(".", "res", "icons", name)
        # 创建一个QSvgRenderer对象来处理SVG文件
        renderer = QSvgRenderer(svg_path)
        # 创建一个足够大的QPixmap来承载SVG渲染结果
        pixmap = QPixmap(renderer.defaultSize())
        pixmap.fill(Qt.transparent)
        # 使用QPainter在QPixmap上绘制SVG
        painter = QPainter(pixmap)
        renderer.render(painter)
        painter.end()
        # 将QPixmap转换为QIcon
        icon = QIcon(pixmap)
        return icon

    @staticmethod
    def buildQPixmap(pixmapName, dir="img"):
        join = os.path.join('.', 'res', dir, pixmapName)
        return QPixmap(join)

    @staticmethod
    def buildQPixmap(path='.', pixmapName='', dir="img"):
        join = os.path.join(path, 'res', dir, pixmapName)
        return QPixmap(join)


class ActionJudge(object):
    @staticmethod
    def isAcceptDrag(event: QDragEnterEvent, endswith: str = ".apk"):
        if event.mimeData().hasUrls():
            url = event.mimeData().urls()[0]
            if url.scheme() == "file":
                path = url.toLocalFile()
                # 检查文件是否是 .apk 文件
                return path.endswith(endswith)
        return False
