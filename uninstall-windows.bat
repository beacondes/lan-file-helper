@echo off
chcp 65001 >nul
title 文件助手 - 卸载
setlocal

set "DIR=%LOCALAPPDATA%\LanFileHelper"

echo 正在停止服务并删除...
taskkill /IM lan-file-helper.exe /F >nul 2>&1
reg delete "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v LanFileHelper /f >nul 2>&1
if exist "%DIR%" rmdir /S /Q "%DIR%"

echo.
echo 已卸载：程序文件与开机自启项已删除。
pause
