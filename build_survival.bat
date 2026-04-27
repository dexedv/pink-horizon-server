@echo off
cd /d "C:\Users\domin\Desktop\Entwicklingen\Mincraftserer Pink Horizon\plugins\ph-survival"
"C:\Users\domin\Desktop\Entwicklingen\maven\apache-maven-3.9.6\bin\mvn.cmd" package -q
if %errorlevel% == 0 (
    echo BUILD_OK
    copy /Y "target\ph-survival-1.0.0.jar" "..\..\..\servers\survival\plugins\ph-survival.jar"
    echo DEPLOYED
) else (
    echo BUILD_FAILED
)
