@echo off
rem Compila o Pulse, roda os testes e copia o APK e as previas para esta pasta.
rem O build roda numa copia local do projeto, fora do Google Drive.
setlocal
set "TC=%LOCALAPPDATA%\PulseToolchain"
set "WORK=%TC%\work"
set "JAVA_HOME=%TC%\jdk\jdk-17.0.21+9"
set "PATH=%JAVA_HOME%\bin;%PATH%"
robocopy "%~dp0." "%WORK%" /MIR /XD build .gradle .kotlin previas /XF Pulse.apk local.properties /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 exit /b 1
echo sdk.dir=%TC:\=/%/sdk> "%WORK%\local.properties"
call "%WORK%\gradlew.bat" -p "%WORK%" :app:testDebugUnitTest :app:lintRelease :app:assembleRelease || exit /b 1

rem O Google Drive as vezes segura o arquivo enquanto sincroniza: tenta de novo e confere o conteudo.
call :copia "%WORK%\app\build\outputs\apk\release\app-release.apk" "%~dp0Pulse.apk" || exit /b 1
robocopy "%WORK%\app\build\previews" "%~dp0previas" *.png /R:5 /W:2 /NFL /NDL /NJH /NJS /NP >nul
if errorlevel 8 echo AVISO: algumas previas nao foram copiadas.
echo APK atualizado: %~dp0Pulse.apk
exit /b 0

:copia
for /l %%i in (1,1,6) do (
    copy /y "%~1" "%~2" >nul 2>&1
    fc /b "%~1" "%~2" >nul 2>&1 && exit /b 0
    ping -n 3 127.0.0.1 >nul
)
echo ERRO: nao consegui copiar %~nx1 para %~2 (o Google Drive pode estar segurando o arquivo).
exit /b 1
