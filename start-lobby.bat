@echo off
set JAVA="C:\Users\domin\AppData\Roaming\Badlion Client\Data\jdk-21.0.2\bin\java.exe"
cd /d "C:\Users\domin\Desktop\Entwicklingen\Mincraftserer Pink Horizon\servers\lobby"
%JAVA% -Xms512M -Xmx1G -XX:+UseG1GC -jar server.jar nogui
