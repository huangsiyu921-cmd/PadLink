@echo off
chcp 65001 >nul
setlocal EnableDelayedExpansion
title PadLink 卸载

echo.
echo   PadLink 卸载
echo   ------------
echo.

rem 先展开成绝对路径，省得带 .. 的路径在别处出岔子
for %%I in ("%~dp0.") do set "SELF_DIR=%%~fI"
for %%I in ("%~dp0..\..\app") do set "FALLBACK_APP=%%~fI"

rem 脚本可能随程序发在 app\ 里，也可能在源码的 tools\ 里
if exist "%~dp0PadLink.Gui.exe" (
    set "APPDIR=!SELF_DIR!"
    set "SELF_INSIDE=1"
) else (
    set "APPDIR=!FALLBACK_APP!"
    set "SELF_INSIDE=0"
)
echo   程序目录：!APPDIR!
echo.

rem 1) 停进程
taskkill /IM PadLink.Gui.exe /F >nul 2>&1
taskkill /IM PadLink.Server.exe /F >nul 2>&1
echo   已停止 PadLink 进程

rem 2) 拆 adb 隧道（正常退出会自己拆，被强杀时可能残留）
where adb >nul 2>&1
if !errorlevel! equ 0 (
    adb reverse --remove tcp:42312 >nul 2>&1
    adb reverse --remove-all >nul 2>&1
    echo   已拆除 adb 隧道
) else (
    echo   没找到 adb，跳过隧道清理
)

rem 3) 删日志等用户数据
if exist "%LocalAppData%\PadLink" (
    rmdir /S /Q "%LocalAppData%\PadLink" >nul 2>&1
    echo   已删除 %LocalAppData%\PadLink
)

rem 4) 删程序目录
if not exist "!APPDIR!" goto keep

if "!SELF_INSIDE!"=="1" (
    rem 脚本自己就在里面，得等它退出后再删
    cd /d "%TEMP%"
    start "" /min powershell -NoProfile -Command "Start-Sleep 2; Remove-Item -LiteralPath '!APPDIR!' -Recurse -Force -ErrorAction SilentlyContinue"
    echo   已安排删除 !APPDIR!（脚本自己在里面，2 秒后执行）
) else (
    rem 脚本在 tools\ 里，可以当场删干净
    cd /d "%TEMP%"
    rmdir /S /Q "!APPDIR!" >nul 2>&1
    if exist "!APPDIR!" (
        echo   ！删除失败。可能还有程序占着这个目录，关掉再删：!APPDIR!
    ) else (
        echo   已删除 !APPDIR!
    )
)

:keep
echo.
echo   有意保留的：
echo     - ViGEmBus 驱动本身。DS4Windows、EMotion 等软件也在用它，删了会连累别人。
echo       确实要连驱动一起卸，用 ViGEmBus 安装包里那个 nefconw.exe（管理员命令行）：
echo         nefconw.exe --remove-device --class {8246D653-8E0E-4B1B-8A2E-3FC8C7EB8E0F}
echo     - 设备管理器里可能还留着 "Unknown" 的 Xbox 360 Controller 条目，
echo       那是 ViGEm 设备注销后的正常残留，不占 XInput 槽位、不影响使用。
echo       想收拾就在设备管理器里勾上「显示隐藏的设备」，再手动删除。
echo     - 手机端的 App。要一并卸掉，插上线执行：adb uninstall com.padlink.app
echo.
pause
