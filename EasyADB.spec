# -*- mode: python ; coding: utf-8 -*-
import os

block_cipher = None

# 获取 .spec 文件所在目录（即项目根目录） SPEC是PyInstaller 内置变量，指向 .spec 文件的完整路径
spec_root = os.path.dirname(os.path.abspath(SPEC))

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
    name='EasyADB',
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
)
coll = COLLECT(
    exe,
    a.binaries,
    a.zipfiles,
    a.datas,
    strip=False,
    upx=True,
    upx_exclude=[],
    name='EasyADB_v1.0.5',  # 输出目录名称
)
