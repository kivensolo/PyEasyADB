import os

from PyQt5.QtGui import QIcon, QPixmap


class IconTool:
    def __init__(self):
        super().__init__()

    def buildQIcon(iconName, dir="img"):
        path = os.path.join(".", "res", dir, iconName)
        return QIcon(path)

    def buildQPixmap(pixmapName):
        return QPixmap(os.path.join('.', 'res', 'img', pixmapName))

