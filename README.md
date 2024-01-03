# PyQt5 实战:
## FIXME
- [] data/logs/xxxx.log存在会在多个路径出现的问题。

## TODO
- [x] 关于
- [x] 录屏

- [ ] 按键模拟 弹出遥控器画面，拦截键盘事件进行按键发送
- [ ] 保存apk到电脑

## 发布
`pyinstaller EasyADB.py --noconsole`
--noconsole 指定不要命令行窗口，否则程序运行的时候，还会多一个黑窗口。

`pyinstaller EasyADB.py --noconsole --hidden-import PySide2.QtXml`

最后记得复制资源文件