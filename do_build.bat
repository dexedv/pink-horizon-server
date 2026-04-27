@echo off
set "JAVA_HOME=C:\Users\domin\AppData\Roaming\Badlion Client\Data\jdk-21.0.2"
set "MVN=C:\Users\domin\Desktop\Entwicklingen\maven\apache-maven-3.9.6\bin\mvn.cmd"
set "BASE=C:\Users\domin\Desktop\Entwicklingen\Mincraftserer Pink Horizon"
set "LOG=C:\temp\build_result.txt"

if not exist C:\temp mkdir C:\temp
echo Building ph-survival... > "%LOG%"
cd /d "%BASE%\plugins\ph-survival"
"%MVN%" package -q >> "%LOG%" 2>&1
if %ERRORLEVEL% EQU 0 (
    echo BUILD_SUCCESS >> "%LOG%"
    copy /Y "target\ph-survival-1.0.0.jar" "%BASE%\servers\survival\plugins\ph-survival.jar" >> "%LOG%"
    echo DEPLOYED >> "%LOG%"
) else (
    echo BUILD_FAILED >> "%LOG%"
)
