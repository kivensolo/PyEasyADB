# -*- mode: python ; coding: utf-8 -*-
import os
import sys

block_cipher = None

# 获取 .spec 文件所在目录（即项目根目录） SPEC是PyInstaller 内置变量，指向 .spec 文件的完整路径
spec_root = os.path.dirname(os.path.abspath(SPEC))

# 添加项目根目录到 sys.path，以便导入项目模块
if spec_root not in sys.path:
    sys.path.insert(0, spec_root)

# 导入版本号（从 src/settings.py）
try:
    from src.settings import APP_VERSION
except ImportError:
    # 如果导入失败，使用默认版本号
    APP_VERSION = '1.0.5'

# 生成带版本号的 exe 名称和目录名称
app_name_with_version = f'EasyADB_v{APP_VERSION}'

a = Analysis(
    ['EasyADB.py'],
    pathex=[],
    binaries=[],
    datas=[
        # 参数1：源路径（绝对路径），打包时从这里读取
        # 参数2：目标路径（相对路径），打包后在 dist/EasyADB/res/
        (os.path.join(spec_root, 'res'), 'res'),
    ],
    hiddenimports=[],
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    win_no_prefer_redirects=False,
    win_private_assemblies=False,
    cipher=block_cipher,
    noarchive=False,
)
pyz = PYZ(a.pure, a.zipped_data, cipher=block_cipher)

exe = EXE(
    pyz,
    a.scripts,
    [],
    exclude_binaries=True,
    name=app_name_with_version,
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=True,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
    icon=os.path.join(spec_root, 'ic_app.ico'),  # 添加图标路径
)
coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name=app_name_with_version,  # 输出目录名称，与 exe 名称保持一致
)
