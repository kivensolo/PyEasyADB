from PyQt5.QtWidgets import QComboBox


class ExtComBox(QComboBox):
    """
    QT的combobox下拉列表的宽度默认情况下与combobox本身的宽度是一致的，
    但是有时候下拉列表的文字很长，显示不开的时候，就需要手动设置其宽度，
    """
    def adjustSize(self):
        max_len = 0
        for index in range(self.count()):
            length = len(self.itemText(index))
            if max_len < length:
                max_len = length
        pt_val = self.font().pointSize()
        # self.setFixedSize(max_len * pt_val * 0.75)

