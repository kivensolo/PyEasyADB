import os

from PyQt5.QtCore import Qt
from PyQt5.QtGui import QIcon, QPixmap, QPainter, QDragEnterEvent
from PyQt5.QtSvg import QSvgRenderer


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
