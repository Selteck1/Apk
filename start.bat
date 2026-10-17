@echo off
setlocal
cd /d "%~dp0"
title Telegram Web Gift Scanner

echo Looking for a Python 3.12 runtime...
set "BASEPY="

if exist "%LocalAppData%\Python\pythoncore-3.12-64\python.exe" set "BASEPY=%LocalAppData%\Python\pythoncore-3.12-64\python.exe"
if not defined BASEPY if exist "%LocalAppData%\Programs\Python\Python312\python.exe" set "BASEPY=%LocalAppData%\Programs\Python\Python312\python.exe"
if not defined BASEPY if exist "C:\Program Files\Python312\python.exe" set "BASEPY=C:\Program Files\Python312\python.exe"

if not defined BASEPY (
  where pymanager >nul 2>nul
  if not errorlevel 1 (
    echo Installing the Python 3.12 runtime with Python Install Manager...
    pymanager install 3.12
    if errorlevel 1 goto no_supported_python
    for /f "delims=" %%P in ('pymanager list --format=exe 3.12 2^>nul') do if not defined BASEPY set "BASEPY=%%P"
  )
)

if not defined BASEPY goto no_supported_python

echo Using:
echo %BASEPY%
"%BASEPY%" --version
if errorlevel 1 goto no_supported_python

if not exist "config.json" (
  copy "config.example.json" "config.json" >nul
  echo Created config.json. Paste the @BotFather token into it, save, then close Notepad.
  notepad "config.json"
)

rem Recreate the local environment if it was made with Python 3.15 or another version.
if exist ".venv\Scripts\python.exe" (
  ".venv\Scripts\python.exe" -c "import sys; raise SystemExit(0 if sys.version_info[:2] == (3, 12) else 1)"
  if errorlevel 1 (
    echo Replacing the old project virtual environment with Python 3.12...
    rmdir /s /q ".venv"
  )
)

if not exist ".venv\Scripts\python.exe" (
  "%BASEPY%" -m venv ".venv"
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

:no_supported_python
echo.
echo Python 3.12 is required for reliable binary wheels.
echo If pymanager was found, its attempt to install Python 3.12 may have failed.
echo Install Python 3.12 from https://www.python.org/downloads/release/python-31210/
echo Then run start.bat again. Do not install Visual C++ just for this app.
pause
exit /b 1

:error
echo.
echo App stopped because of an error. Read the message above.
pause
exit /b 1

:end
pause
