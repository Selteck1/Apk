@echo off
setlocal
cd /d "%~dp0"
title Telegram Web Gift Scanner

where python >nul 2>nul
if errorlevel 1 goto no_python

python --version
if errorlevel 1 goto no_runtime

python -c "import sys; raise SystemExit(0 if sys.version_info >= (3,11) else 1)"
if errorlevel 1 goto old_python

if not exist "config.json" (
  copy "config.example.json" "config.json" >nul
  echo Created config.json. Paste the @BotFather token into it, save, then close Notepad.
  notepad "config.json"
)

if not exist ".venv\Scripts\python.exe" (
  python -m venv .venv
  if errorlevel 1 goto error
)

call ".venv\Scripts\activate.bat"
python -m pip install --upgrade pip
if errorlevel 1 goto error
python -m pip install -r requirements.txt
if errorlevel 1 goto error
python -m playwright install chromium
if errorlevel 1 goto error
python main.py
if errorlevel 1 goto error
goto end

:no_python
echo The "python" command was not found in PATH.
echo Open CMD and run: where python
echo If Python is installed, add its installation folder to your user PATH.
pause
exit /b 1

:no_runtime
echo Windows found a python.exe command, but it could not start a Python runtime.
echo Run: python --version
echo If Python Install Manager is installed, install a runtime with: py install 3.12
echo If the "py" command is unavailable, install Python 3.12 from https://www.python.org/downloads/windows/
pause
exit /b 1

:old_python
echo Python 3.11 or newer is required.
echo Download Python from https://www.python.org/downloads/windows/
pause
exit /b 1

:error
echo.
echo App stopped because of an error. Read the message above.
pause
exit /b 1

:end
pause
