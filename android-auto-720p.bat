@echo off
rem Abre o simulador do Android Auto (Desktop Head Unit) em 1280x720, ligado ao celular pelo cabo.
rem Antes: celular no USB, e no Android Auto > (menu) > "Iniciar servidor da unidade principal".
setlocal
set "DHU="
for %%S in ("%LOCALAPPDATA%\PulseToolchain\sdk" "%LOCALAPPDATA%\Android\Sdk" "%LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Local\Android\Sdk") do (
    if not defined DHU if exist "%%~S\extras\google\auto\desktop-head-unit.exe" set "DHU=%%~S\extras\google\auto"
)
if not defined DHU (
    echo Simulador nao encontrado. Instale "extras;google;auto" com o sdkmanager.
    pause
    exit /b 1
)

set "ADB="
for %%A in ("C:\platform-tools\adb.exe" "%LOCALAPPDATA%\PulseToolchain\sdk\platform-tools\adb.exe" "%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe") do (
    if not defined ADB if exist "%%~A" set "ADB=%%~A"
)
if not defined ADB (
    echo adb nao encontrado.
    pause
    exit /b 1
)

"%ADB%" forward tcp:5277 tcp:5277 || (
    echo Celular nao encontrado no cabo. Conecte e permita a depuracao USB.
    pause
    exit /b 1
)

cd /d "%DHU%"
desktop-head-unit.exe -c "%DHU%\config\default_720p.ini"
"%ADB%" forward --remove tcp:5277 >nul 2>&1
