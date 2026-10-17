@echo off
setlocal
cd /d "%~dp0"
title Telegram Web Gift Scanner

where py >nul 2>nul
if errorlevel 1 (
  echo Python 3.11+ not found. Install it from https://www.python.org/downloads/windows/
  echo Enable "Add python.exe to PATH" in the installer.
  pause
  exit /b 1
)

if not exist "config.json" (
  copy "config.example.json" "config.json" >nul
  echo Created config.json. Paste the @BotFather token into it, save, then close Notepad.
  notepad "config.json"
)

if not exist ".venv\Scripts\python.exe" (
  py -3 -m venv .venv
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

:error
echo.
echo App stopped because of an error. Read the message above.
pause
exit /b 1

:end
pause
