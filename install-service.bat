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
set "DATADIR=%USERPROFILE%\Downloads\LanFileHelper"

echo ==========================================
echo   文件助手 —— 安装为 Windows 服务
echo ==========================================
echo.

if not exist "%DIR%" mkdir "%DIR%"
if not exist "%DATADIR%" mkdir "%DATADIR%"

echo [1/5] 复制程序文件...
copy /Y "%~dp0lan-file-helper.exe" "%DIR%\" >nul
if exist "%~dp0public" xcopy /E /I /Y "%~dp0public" "%DIR%\public" >nul

echo [2/5] 复制服务包装器...
copy /Y "%~dp0lan-file-helper-service.exe" "%DIR%\" >nul

echo [3/5] 生成服务配置（数据目录指向下载文件夹）...
> "%DIR%\lan-file-helper-service.xml" echo ^<service^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<id^>LanFileHelper^</id^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<name^>LanFileHelper (文件助手)^</name^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<description^>局域网文件助手 - Windows 端文件传输服务^</description^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<executable^>%DIR%\lan-file-helper.exe^</executable^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<workingdirectory^>%DIR%^</workingdirectory^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<env name="DATA_DIR" value="%DATADIR%"/^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<logmode^>rotate^</logmode^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<logpath^>%DIR%\logs^</logpath^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<onfailure action="restart" delay="10 sec"/^>
>> "%DIR%\lan-file-helper-service.xml" echo   ^<startmode^>Automatic^</startmode^>
>> "%DIR%\lan-file-helper-service.xml" echo ^</service^>

echo [4/5] 放行 Windows 防火墙...
netsh advfirewall firewall delete rule name="LanFileHelper" >nul 2>&1
netsh advfirewall firewall add rule name="LanFileHelper" dir=in action=allow program="%DIR%\lan-file-helper.exe" enable=yes >nul

echo [5/5] 安装并启动服务...
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
echo.
echo   文件保存位置：%DATADIR%\uploads
echo   （即「下载」文件夹里的 LanFileHelper 文件夹）
echo.
echo   管理：运行 services.msc 可查看/停止/重启
echo   手机连同一 WiFi，访问电脑 IP 的 3000 端口
echo ==========================================
echo.
pause
