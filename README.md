# 简介
EasyADB是基于PyQt5框架实现的一款便捷进行ADB操作的软件，包含了开发者在工作过程中频繁使用的大部分功能。
目的是让ADB相关操作变得更加简单方便。

# 工程说明
整体基于**python 3.11.1**,更高版本的兼容性需开发者自测，理论上3.17、3.18等应该都没问题。

## 虚拟环境
使用IDE导入此项目后，python解释器建议选择虚拟环境中的解释器，路径为:`./venv/Scripts/pythpn.exe`。<br>
在工程根目录下运行以下命令来创建虚拟环境：

`python -m venv ea_venv` <br>
`ea_venv`为环境名，可以自定义。

创建虚拟环境后，进入`ea_venv/Scripts`目录，执行`activate`可进入虚拟环境。<br>
退出:`deactivate`

## 依赖库安装
所需依赖库信息，比如PyQt5、lxml、pyinstaller等，已经在`requriements.txt`文件中包含。<br>
安装命令: `pip install -r requirements.txt`

**注意**：不同版本的python与依赖库之间有版本兼容性问题存在，所以如果在安装中报错，需要自己修改对应库的版本号。


## 结构说明
项目根目录/<br>
├─ config/<br>
│  ├─ cmdConfig.xml				左侧命令行区域配置文件<br>
│  ├─ function_templates.xml	主功能区域配置文件<br>
│  └─ menus_ui.xml  			菜单栏UI配置文件<br>
├─ pyqtWidgets/   一些pyqt基础组件的demo文件<br>
├─ data/<br>
│   ├─ logs/	  应用日志储存目录<br>
│   └─ easyADB.db 应用本地数据库文件<br>
├─ res/           应用资源文件<br>
├─ src/          应用源码目录<br>
│  ├─ component/   qt组件目录，		<br>
│  ├─ logcat/	   日志模块<br>
│  ├─ widget/      各种控件<br>
│  ├─ settings.py  应用设置模块<br>
│  ├─ ......<br>
│  └─ DataBase.py  数据库模块<br>
├─ utils/  		    早期的工具类源码目录(暂时未移至src)<br>
├─ README.md        ReadMe文件<br>
├─ EasyADB.py       程序App启动主入口<br>
└─ requriements.txt requriements文件<br>



# 更新记录
## v 1.0.2
1.[功能] 增加实时logcat功能! 支持日志级别、进程pid、指定文本的过滤。(暂不支持搜索);<br>
2.[功能] 设备列表增加对offline设备的状态展示，支持对offline设备的断开与重连操作;<br>
3.[优化] 设备信息栏目区域UI优化，调整margin，降低整体高度。<br>
4.[优化] 优化Open shell功能，打开系统自带的控制台时，默认进入目标设备的shell模式
5.[修复] 修复已知bugs;<br>

## v 1.0.1
1.[功能] 增加应用参数设置区域, 增加自定义下拉选择框，可实现对数据条目的管理;<br>
2.[功能] 增加广播发送功能;<br>
3.[功能] 新增ContentProvider查询功能;<br>
4.[功能] 新增广播发送功能;<br>
5.[功能] 增加异常捕获处理器，发生异常时不会崩溃闪退，且支持致命异常打印及日志记录;<br>
6.[功能] 卸载应用行为增加弹窗确认功能;<br>
7.[功能] 运行时进程信息列表动态更新，设备离线后会进行清空展示;<br>
8.[优化] 设备备注名称修改后，对话框自动关闭;<br>
9.[重构] 重构命令数据结构及数据传递流程，增强命令模式的扩展性;<br>
10.[修复] 修复已知bugs;<br>

# 发布说明
`pyinstaller EasyADB.py`<br>
参数说明：<br>
--noconsole : 指定不要命令行窗口，否则程序运行的时候，还会多一个黑窗口。但是在执行命令操作时，会闪现，体验不好。

打包完成后，会生成dist目录，打包后的文件夹在此目录。但是打包时不会打包资源文件，
所以需要手动复制资源文件至打包目录。


## TODO
- [ ] 按键模拟 弹出遥控器画面，拦截键盘事件进行按键发送
- [ ] 保存apk到电脑