@echo off
chcp 65001 >nul
setlocal

net session >nul 2>&1
if errorlevel 1 (
    echo [错误] 卸载需要管理员权限，请右键「以管理员身份运行」。
    pause
    exit /b 1
)

set "DIR=%LOCALAPPDATA%\LanFileHelper"

echo 正在停止并卸载服务...
cd /d "%DIR%" 2>nul
lan-file-helper-service.exe stop 2>nul
lan-file-helper-service.exe uninstall 2>nul
netsh advfirewall firewall delete rule name="LanFileHelper" >nul 2>&1
if exist "%DIR%" rmdir /S /Q "%DIR%"

echo 已卸载完成：服务与程序文件均已删除。
pause
