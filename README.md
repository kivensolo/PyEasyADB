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

## 主要功能说明
### 远程文件操作管理（TODO）
对于经常使用的目录，可以进行收藏，或者直接编辑 ./bin/favorite.txt 文件，一行一个目录路径，这个功能比较方便实用。
支持pull下载远程文件到本地。
上传文件直接拖放即可，方便快捷。

### 画面实时预览
使用 scrcpy 实现实时预览手机画面的效果。
从 https://github.com/Genymobile/scrcpy/releases 下载编译好的版本，
例如下载 scrcpy-win64-v1.24.zip ，解压缩后修改修改文件夹名为 scrcpy-win64 放置在 <User>\AppData\Local\EasyADB\tools/tool目录下，
EasyADB会自动调用tool/scrcpy-win64/scrcpy.exe；
如果要升级替换 scrcpy 的版本，只需要替换 scrcpy-win64 目录下的文件即可，实现无缝升级；

# FIXME
- [ ] [BUG]弹窗位置要优化，多屏设备的时候，在屏幕2点击，会展示在屏幕1中心.


# 更新记录
## v 1.0.5 TODO
1. [] [功能] 增加frida支持;

## v 1.0.4
1. [x] [优化] 修复真机通过有线连接时出现的连接问题。
2. [x] [功能] 🔥 增加apk文件信息查看功能(快捷键ALT+H)，支持应用包名、应用名、版本号、签名、签名版本、是否Launcher应用、
是否系统应用、ICON图标、文件大小、文件MD5、创建时间等信息查看，右键图标支持导出;


## v 1.0.3
1. [x] [功能] 增加android platform-tools 的依赖，提供独立的ADB环境支撑;
2. [x] [功能] 🔥 增加设备自动检测功能，自动同步设备状态;
3. [x] [功能] 🔥 增加实时预览设备功能;
4. [x] [功能] 日志窗口与实时日志窗口新增‘Scroll To End’的功能, 使用新图标;
5. [x] [优化] 优化屏幕截图，提高截图时的应用响应速度;
6. [x] [优化] 部分模块优化, bugs修复;
7. [x] [重构] 数据库文件位置改动, 改为用户应用数据目录(涉及数据手动迁移);

数据库手动迁移说明，将原有.\data\easyADB.db 手动复制到 C:\Users\<your_name>\AppData\Local\EasyADB\data中即可。


## v 1.0.2
1.[功能] 🔥 增加实时logcat功能! 支持日志级别、进程pid、指定文本的过滤。(暂不支持搜索);<br>
2.[功能] 设备列表增加对offline设备的状态展示，支持对offline设备的断开与重连操作;<br>
3.[优化] 设备信息栏目区域UI优化，调整margin，降低整体高度。<br>
4.[优化] 🔥 优化Open shell功能，打开系统自带的控制台时，默认进入目标设备的shell模式
5.[修复] 修复已知bugs;<br>

## v 1.0.1
1.[功能] 🔥 增加应用参数设置区域, 增加自定义下拉选择框，可实现对数据条目的管理;<br>
2.[功能] 🔥 增加广播发送功能;<br>
3.[功能] 🔥 新增ContentProvider查询功能;<br>
4.[功能] 🔥 新增广播发送功能;<br>
5.[功能] 增加异常捕获处理器，发生异常时不会崩溃闪退，且支持致命异常打印及日志记录;<br>
6.[功能] 卸载应用行为增加弹窗确认功能;<br>
7.[功能] 运行时进程信息列表动态更新，设备离线后会进行清空展示;<br>
8.[优化] 设备备注名称修改后，对话框自动关闭;<br>
9.[重构] 重构命令数据结构及数据传递流程，增强命令模式的扩展性;<br>
10.[修复] 修复已知bugs;<br>

# 发布说明
## 打包说明
1. 确保不在`虚拟环境`下，如果处于虚拟环境下，则进入`Scripts`目录，执行`deactivate`退出。
2. 执行以下命令打包(建议不带参数)：
`pyinstaller --onefile EasyADB.py`<br>
`pyinstaller EasyADB.py`<br>
参数说明：<br>
--noconsole : 指定不要命令行窗口，否则程序运行的时候，还会多一个黑窗口。但是在执行命令操作时，会闪现，体验不好。
--onefile: 生成单个exe文件;

打包完成后，会生成dist目录，打包后的文件夹在此目录。但是打包时不会打包资源文件，
所以需要手动复制资源文件至打包目录。

## 关于CA证书文件的说明
v1.0.3开始，使用了requests库，通过PyInstaller打包独立文件后的程序会出现:<br>
OSError: Could not find a suitable TLS CA certificate bundle, invalid path:。
原因是使用`--onefile`打包时，requests 库释放在用户临时文件夹内的 CA 证书没有被一起打包，所以运行时无法找到。

解决方案就是将证书手动加入代码内,将本地 CA 证书的路径临时地写入系统环境变量中。我这里指定的路径为'.\certifi\cacert.pem'<br>
cacert.pem 文件可以从 https://curl.se/docs/caextract.html 下载到;

【注意】: 打包成非独立文件时，此证书文件就会被打包进去，所以不需要再手动复制了。

## TODO
- [ ] 保存apk到电脑