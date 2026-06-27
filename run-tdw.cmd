@echo off
setlocal

set "SCRIPT_DIR=%~dp0"
set "POWERSHELL_EXE=powershell.exe"

where pwsh.exe >nul 2>nul
if "%ERRORLEVEL%"=="0" set "POWERSHELL_EXE=pwsh.exe"

title TDW
"%POWERSHELL_EXE%" -NoProfile -ExecutionPolicy Bypass -File "%SCRIPT_DIR%run-tdw.ps1" %*
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo TDW exited with code %EXIT_CODE%.
    pause
)

exit /b %EXIT_CODE%
