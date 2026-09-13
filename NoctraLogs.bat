@echo off
title Noctra App Logs
echo Waiting for wireless log stream...
adb logcat -v color *:D | findstr /i "com.noctra.app"
pause