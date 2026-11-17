@echo off
rem Abre o simulador do Android Auto (DHU) em 1280x720.
rem Antes: celular no USB, e no Android Auto > Config. do desenvolvedor > "Iniciar servidor da unidade principal".
set SDK=%LOCALAPPDATA%\Packages\Claude_pzs8sxrjxfjjc\LocalCache\Local\Android\Sdk
if not exist "%SDK%\extras\google\auto" set SDK=%LOCALAPPDATA%\Android\Sdk
set DHU=%SDK%\extras\google\auto

if exist "%SDK%\platform-tools\adb.exe" "%SDK%\platform-tools\adb.exe" forward tcp:5277 tcp:5277

cd /d "%DHU%"
desktop-head-unit.exe -c "%DHU%\config\default_720p.ini"
