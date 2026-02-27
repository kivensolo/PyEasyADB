# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EasyPyADB is a Windows-based GUI application built with PyQt5 that provides a user-friendly interface for ADB (Android Debug Bridge) operations. It simplifies Android device management and debugging tasks for developers.

**Language**: Python 3.11.1+ (compatible with 3.17+, 3.18+)
**GUI Framework**: PyQt5

## Development Commands

### Virtual Environment Setup

```bash
# Create virtual environment
python -m venv ea_venv

# Activate (Windows)
ea_venv\Scripts\activate

# Deactivate
deactivate
```

### Install Dependencies

```bash
pip install -r ea_venv/Scripts/requirements.txt
```

Key dependencies:
- PyQt5 5.15.10
- lxml 5.1.0
- pyinstaller 6.3.0
- requests (via pip._vendor.requests)
- pywin32-ctypes

### Running the Application

```bash
python EasyADB.py
```

### Building with PyInstaller

```bash
# Single executable with console
pyinstaller --onefile EasyADB.py

# Directory-based package (recommended)
pyinstaller EasyADB.py

# Without console (may flash during operations)
pyinstaller --noconsole --onefile EasyADB.py
```

**Note**: PyInstaller does not bundle resource files. Manually copy the `res/` directory to the `dist/` folder after building.

**CA Certificate**: When using `--onefile`, include `certifi/cacert.pem` in the distribution directory to fix requests library TLS errors. Download from: https://curl.se/docs/caextract.html

## Architecture

### Entry Point Flow

`EasyADB.py` → `AndroidDependencies.Check()` → `MainWindow` → QApplication main loop

The application:
1. Sets up PyQt5 with high DPI scaling support
2. Configures custom exception handling via `sys.excepthook`
3. Initializes Android dependencies (platform-tools, scrcpy) in `%LOCALAPPDATA%/EasyADB/`
4. Creates and runs the main window

### Core Layers

**GUI Layer** (`src/`):
- `MainWindow.py` - Main window with device tree view, functional buttons, menu bar
- `BottomWindow.py` - Bottom tab widget (console output + live logcat viewer)
- `CenterWindow.py` - Central functional area with dynamic UI from XML templates
- `BaseWindow.py` - Base window class
- `MenuBar.py`, `ToolBar.py` - Navigation components
- `widget/` - Custom UI components (dialogs, widgets, screen recording)

**Business Logic Layer** (`utils/`):
- `ADBTools.py` - Core ADB operations wrapper using `ActionCmdParams`
- `PackageManager.py` - APK management utilities
- `CmdExecutor.py` - Command execution with threading
- `Utils.py`, `Tools.py`, `UITools.py` - General utilities

**Data Layer**:
- `src/DataBase.py` - SQLite database for device configs, preferences, templates
- `src/settings.py` - Application configuration constants

### Configuration System

All configuration files are in `res/config/`:
- `cmdConfig.xml` - ADB command templates for left panel (uses `{0}` placeholder for package name)
- `function_templates.xml` - UI template definitions for main area
- `menus_ui.xml` - Menu bar configuration
- `AppConfig.ini` - Application settings

Dynamic UI is loaded from XML templates. The `ActionCmdParams` class handles command assembly with macro replacement.

### Device Management

- `DevicesWatcher.py` - Monitors device connection status
- `TreeItemType.py` - Tree item type definitions for device hierarchy
- Device status: online, offline, unauthorized
- Device alias/nickname support stored in database

### Logcat System

- `src/logcat/log.py` - Logging utilities with `z_logger`
- Live logcat viewer with filtering (by PID, log level, text)
- Log limit: `LIVE_LOG_CONUTS_LIMITS = 3000` (default in settings.py)
- Logs stored in `logs/` with date-based naming

## File Paths

**Application Data** (`%LOCALAPPDATA%/EasyADB/`):
- `platform-tools/` - ADB tools (auto-downloaded)
- `tools/scrcpy-win64/` - Screen mirroring tool
- `data/easyADB.db` - SQLite database
- `tmp/` - Temporary files

**Project Structure**:
```
BASE_PATH = os.getcwd()  # Project root
LOGS_PATH = logs/        # Application logs
SCRCPY_PATH = tool/scrcpy-win64/  # Local scrcpy backup
```

## Key Patterns

### Command Execution Pattern

`ActionCmdParams` class wraps ADB commands:
- `isShellMode`: Use `adb shell` vs direct adb command
- `needDstPkg`: Require package name validation
- `target_device_ip`: Device serial/IP
- `target_app`: Package name (for `{0}` placeholder)

Commands are assembled via `getAdbCMD()` with automatic macro replacement.

### Dynamic UI Loading

UI templates loaded from XML, with elements per row configured by `CTNTER_WINDOW_EVERY_ROW_SIZE = 5` in settings.py.

### Exception Handling

Custom `exception_handler()` in `EasyADB.py` catches all exceptions, logs them, and prevents crashes.

## Common Issues

**Multi-monitor dialog positioning**: Dialogs appear on primary screen center regardless of click location (known FIXME).

**Platform-tools missing**: Auto-downloaded on first run to `%LOCALAPPDATA%/EasyADB/platform-tools/`.

**Scrcpy missing**: Auto-downloaded when screen mirroring is first used (may require VPN for GitHub access).

**Database migration**: When upgrading from v1.0.2, manually move `.\data\easyADB.db` to `%LOCALAPPDATA%\EasyADB\data\`.
