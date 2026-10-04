@echo off
title GenBank - Core Platform Launcher
cd /d "%~dp0"

echo ======================================================================
echo                     GENBANK ALL-IN-ONE LAUNCHER                      
echo ======================================================================
echo.

:: 1. Check if MySQL is running on port 3306
echo [1/3] Checking MySQL Database on port 3306...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t = Test-NetConnection -ComputerName 127.0.0.1 -Port 3306 -InformationLevel Quiet; if (-not $t) { exit 1 }" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       MySQL is stopped. Starting MySQL Server daemon...
    start "GenBank MySQL Server" /min "D:\Download\mysql-enterprise-9.7.1_winx64_bundle\mysql-commercial-9.7.1-winx64\mysql-commercial-9.7.1-winx64\bin\mysqld.exe" --defaults-file="D:\Download\mysql-enterprise-9.7.1_winx64_bundle\mysql-commercial-9.7.1-winx64\mysql-commercial-9.7.1-winx64\my.ini" --console
    ping 127.0.0.1 -n 4 >nul
) else (
    echo       MySQL is already ONLINE on port 3306.
)

:: 2. Check if FastAPI OTP service is running on port 8000
echo.
echo [2/3] Checking FastAPI OTP Microservice on port 8000...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$t = Test-NetConnection -ComputerName 127.0.0.1 -Port 8000 -InformationLevel Quiet; if (-not $t) { exit 1 }" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       Starting Python OTP Microservice...
    start "GenBank OTP Microservice (Port 8000)" cmd /k "cd /d "%~dp0Auth-Service" && python main.py"
    ping 127.0.0.1 -n 3 >nul
) else (
    echo       FastAPI Microservice is already ONLINE on port 8000.
)

:: 3. Auto-open browser when Spring Boot port 8080 becomes available
echo.
echo [3/3] Starting Spring Boot NetBanking Platform...
echo ======================================================================
echo   GenBank Web Portal URL : http://localhost:8080/
echo   CBS Admin Terminal     : http://localhost:8080/ (CBS Admin tab)
echo   FastAPI OTP Service    : http://127.0.0.1:8000/docs
echo ======================================================================
echo.
echo Launching default web browser in background once server is live...

:: Background browser launcher (waits 3 seconds then opens default browser)
start "" cmd /c "ping 127.0.0.1 -n 4 >nul & start http://localhost:8080/"

:: Run Spring Boot (fast-boot with JAR if compiled, or via Maven wrapper)
if exist "target\GenBank-1.0.jar" (
    echo Launching optimized binary: target\GenBank-1.0.jar
    java -jar target\GenBank-1.0.jar
) else (
    echo Launching via Maven Wrapper...
    call mvnw.cmd spring-boot:run
)

pause
