@echo off
title Pink Horizon - Plugin Builder
color 0A

set JAVA_HOME=C:\Users\domin\AppData\Roaming\Badlion Client\Data\jdk-21.0.2
set MVN=%~dp0..\maven\apache-maven-3.9.6\bin\mvn.cmd
set BASE=%~dp0

echo ============================================
echo   Pink Horizon - Plugins werden gebaut...
echo ============================================
echo.

cd /d "%BASE%plugins"
"%MVN%" clean package -q
if %ERRORLEVEL% NEQ 0 (
    echo FEHLER beim Kompilieren!
    pause
    exit /b 1
)

echo Plugins gebaut! Kopiere in Server-Ordner...
echo.

rem Core auf alle Server
copy /Y "ph-core\target\ph-core-1.0.0.jar"           "..\..\Mincraftserer Pink Horizon\servers\lobby\plugins\"
copy /Y "ph-core\target\ph-core-1.0.0.jar"           "..\..\Mincraftserer Pink Horizon\servers\survival\plugins\"
copy /Y "ph-core\target\ph-core-1.0.0.jar"           "..\..\Mincraftserer Pink Horizon\servers\skyblock\plugins\"
copy /Y "ph-core\target\ph-core-1.0.0.jar"           "..\..\Mincraftserer Pink Horizon\servers\minigames\plugins\"

rem Server-spezifische Plugins
copy /Y "ph-lobby\target\ph-lobby-1.0.0.jar"         "..\servers\lobby\plugins\"
copy /Y "ph-survival\target\ph-survival-1.0.0.jar"   "..\servers\survival\plugins\"
copy /Y "ph-skyblock\target\ph-skyblock-1.0.0.jar"   "..\servers\skyblock\plugins\"
copy /Y "ph-minigames\target\ph-minigames-1.0.0.jar" "..\servers\minigames\plugins\"

echo.
echo ============================================
echo   Fertig! Starte den Server mit
echo   START_ALLE_SERVER.bat
echo ============================================
pause
