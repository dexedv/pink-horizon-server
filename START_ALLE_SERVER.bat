@echo off
title Pink Horizon - Server Manager
color 0D

set JAVA="C:\Users\domin\AppData\Roaming\Badlion Client\Data\jdk-21.0.2\bin\java.exe"
set BASE=%~dp0

echo ============================================
echo   Pink Horizon - Netzwerk startet...
echo ============================================
echo.

echo [1/6] Starte Velocity Proxy (Port 25565)...
start "PH-Proxy" cmd /k "cd /d "%BASE%proxy" && %JAVA% -Xms256M -Xmx512M -XX:+UseG1GC -jar velocity.jar"
timeout /t 3 /nobreak >nul

echo [2/6] Starte Lobby (Port 25566)...
start "PH-Lobby" cmd /k "cd /d "%BASE%servers\lobby" && %JAVA% -Xms512M -Xmx1G -XX:+UseG1GC -jar server.jar nogui"
timeout /t 2 /nobreak >nul

echo [3/6] Starte Survival (Port 25567)...
start "PH-Survival" cmd /k "cd /d "%BASE%servers\survival" && %JAVA% -Xms512M -Xmx2G -XX:+UseG1GC -jar server.jar nogui"
timeout /t 2 /nobreak >nul

echo [4/6] Starte SkyBlock (Port 25568)...
start "PH-SkyBlock" cmd /k "cd /d "%BASE%servers\skyblock" && %JAVA% -Xms512M -Xmx2G -XX:+UseG1GC -jar server.jar nogui"
timeout /t 2 /nobreak >nul

echo [5/6] Starte Minigames (Port 25569)...
start "PH-Minigames" cmd /k "cd /d "%BASE%servers\minigames" && %JAVA% -Xms512M -Xmx2G -XX:+UseG1GC -jar server.jar nogui"
timeout /t 2 /nobreak >nul

echo [6/6] Starte Generators - IdleForge (Port 25571)...
start "PH-Generators" cmd /k "cd /d "%BASE%servers\generators" && %JAVA% -Xms512M -Xmx2G -XX:+UseG1GC -jar server.jar nogui"

echo.
echo ============================================
echo   Alle Server gestartet!
echo   Verbinde mit: localhost:25565
echo ============================================
pause
