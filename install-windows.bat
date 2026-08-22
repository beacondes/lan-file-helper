@echo off
chcp 65001 >nul
title 文件助手 - 安装
setlocal

set "DIR=%LOCALAPPDATA%\LanFileHelper"

echo ==========================================
echo   文件助手 —— 安装为开机自启（后台运行）
echo ==========================================
echo.

if not exist "%DIR%" mkdir "%DIR%"

echo [1/3] 复制程序文件...
copy /Y "%~dp0lan-file-helper.exe" "%DIR%\" >nul
if exist "%~dp0public" xcopy /E /I /Y "%~dp0public" "%DIR%\public" >nul

echo [2/3] 创建后台启动脚本...
> "%DIR%\start-hidden.vbs" echo Set WshShell = CreateObject("WScript.Shell")
>> "%DIR%\start-hidden.vbs" echo WshShell.Run """%DIR%\lan-file-helper.exe""", 0, False

echo [3/3] 写入开机自启注册表...
reg add "HKCU\Software\Microsoft\Windows\CurrentVersion\Run" /v LanFileHelper /t REG_SZ /d "wscript.exe \"%DIR%\start-hidden.vbs\"" /f >nul

echo 立即在后台启动服务...
wscript "%DIR%\start-hidden.vbs"

echo.
echo [完成] 服务已在后台运行，并已设为开机自启。
echo.
echo 使用方式：
echo   手机连同一 WiFi，用浏览器或 APP 访问电脑的局域网地址（端口 3000）。
echo   查看电脑 IP：运行 ipconfig，找「IPv4 地址」。
echo   首次运行若 Windows 防火墙弹窗，请点「允许访问」。
echo.
pause
