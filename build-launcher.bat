@echo off
cd /d "%~dp0"

set CSC=
for /f "delims=" %%i in ('dir /b /s C:\Windows\Microsoft.NET\Framework64\csc.exe 2^>nul') do set CSC=%%i
if not defined CSC for /f "delims=" %%i in ('dir /b /s C:\Windows\Microsoft.NET\Framework\csc.exe 2^>nul') do set CSC=%%i

if not defined CSC (
    echo [ERROR] csc.exe not found. Install .NET Framework 4.x,
    echo or use launch4j instead.
    pause
    exit /b 1
)

echo Using compiler: %CSC%
"%CSC%" /nologo /target:winexe /platform:anycpu /out:VibePlayer.exe VibePlayerLauncher.cs

if errorlevel 1 (
    echo [ERROR] Build failed.
) else (
    echo.
    echo Done! VibePlayer.exe created. Run it.
)
pause
