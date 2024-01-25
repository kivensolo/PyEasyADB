import os

from PyQt5.QtGui import QIcon, QPixmap


class IconTool:
    def __init__(self):
        super().__init__()

    @staticmethod
    def buildQIcon(iconName, dir="img"):
        path = os.path.join(".", "res", dir, iconName)
        return QIcon(path)

    @staticmethod
    def buildQPixmap(pixmapName, dir="img"):
        join = os.path.join('.', 'res', dir, pixmapName)
        return QPixmap(join)

