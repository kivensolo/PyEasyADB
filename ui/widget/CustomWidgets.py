from PyQt5.QtWidgets import QLineEdit


class DraggableLineEdit(QLineEdit):
    """
    可接收文件拖动进入事件的自定义QLineEdit
    """
    def __init__(self, parent=None):
        super(DraggableLineEdit, self).__init__(parent)
        self.setAcceptDrops(True)

    def dragEnterEvent(self, event):
        if event.mimeData().hasUrls():
            url = event.mimeData().urls()[0]
            if url.scheme() == "file":
                path = url.toLocalFile()
                if path.endswith('.apk'):  # 检查文件是否是 .apk 文件
                    event.accept()
                else:
                    event.ignore()
        else:
            event.ignore()

    def dropEvent(self, event):
        if event.mimeData().hasUrls():
            url = event.mimeData().urls()[0]
            if url.scheme() == "file":
                path = url.toLocalFile()
                if path.endswith('.apk'):  # 检查文件是否是 .apk 文件
                    self.setText(path)
                    event.accept()
                else:
                    event.ignore()
        else:
            event.ignore()


