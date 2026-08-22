@echo off
chcp 65001 >nul
setlocal

net session >nul 2>&1
if errorlevel 1 (
    echo [错误] 安装服务需要管理员权限。
    echo 请右键本脚本，选择「以管理员身份运行」。
    pause
    exit /b 1
)

set "DIR=%LOCALAPPDATA%\LanFileHelper"

echo ==========================================
echo   文件助手 —— 安装为 Windows 服务
echo ==========================================
echo.

if not exist "%DIR%" mkdir "%DIR%"

echo [1/4] 复制程序文件...
copy /Y "%~dp0lan-file-helper.exe" "%DIR%\" >nul
if exist "%~dp0public" xcopy /E /I /Y "%~dp0public" "%DIR%\public" >nul

echo [2/4] 复制服务包装器...
copy /Y "%~dp0lan-file-helper-service.exe" "%DIR%\" >nul
copy /Y "%~dp0lan-file-helper-service.xml" "%DIR%\" >nul

echo [3/4] 放行 Windows 防火墙...
netsh advfirewall firewall delete rule name="LanFileHelper" >nul 2>&1
netsh advfirewall firewall add rule name="LanFileHelper" dir=in action=allow program="%DIR%\lan-file-helper.exe" enable=yes >nul

echo [4/4] 安装并启动服务...
cd /d "%DIR%"
lan-file-helper-service.exe install
if errorlevel 1 (
    echo.
    echo [错误] 服务安装失败，请检查是否以管理员身份运行。
    pause
    exit /b 1
)
lan-file-helper-service.exe start

echo.
echo ==========================================
echo   [完成] 服务已安装并启动！
echo.
echo   服务名：LanFileHelper（文件助手）
echo   已设为开机自动运行，无窗口、无需登录
echo   管理：运行 services.msc 可查看/停止/重启
echo.
echo   手机连同一 WiFi，访问电脑 IP 的 3000 端口
echo ==========================================
echo.
pause
