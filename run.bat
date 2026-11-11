@echo off
title GenBank - Core Platform Launcher
cd /d "%~dp0"

echo ======================================================================
echo                     GENBANK ALL-IN-ONE LAUNCHER                      
echo ======================================================================
echo.

:: 0. Load local environment configurations from .env if present
if exist ".env" (
    echo [0/3] Loading local environment configurations from .env...
    for /f "usebackq eol=# tokens=1* delims==" %%i in (".env") do (
        if not "%%i"=="" set "%%i=%%j"
    )
)

:: 1. Check if MySQL is running on port 3306 (Ultra-fast native check)
echo [1/3] Checking MySQL Database on port 3306...
netstat -ano | findstr ":3306 " >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       MySQL is stopped. Starting MySQL Server daemon...
    start "GenBank MySQL Server" /min "D:\Download\mysql-enterprise-9.7.1_winx64_bundle\mysql-commercial-9.7.1-winx64\mysql-commercial-9.7.1-winx64\bin\mysqld.exe" --defaults-file="D:\Download\mysql-enterprise-9.7.1_winx64_bundle\mysql-commercial-9.7.1-winx64\mysql-commercial-9.7.1-winx64\my.ini" --console
    timeout /t 2 /nobreak >nul
    echo       MySQL is now ONLINE on port 3306.
) else (
    echo       MySQL is already ONLINE on port 3306.
)

:: Detect active Python runtime
set "PY_CMD=python"
if exist "%LOCALAPPDATA%\Python\bin\python.exe" set "PY_CMD=%LOCALAPPDATA%\Python\bin\python.exe"
if exist "%LOCALAPPDATA%\Python\pythoncore-3.14-64\python.exe" set "PY_CMD=%LOCALAPPDATA%\Python\pythoncore-3.14-64\python.exe"

:: 2. Check if FastAPI OTP service is running on port 8000
echo.
echo [2/3] Checking FastAPI OTP Microservice on port 8000...
netstat -ano | findstr ":8000 " >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       Starting Python OTP Microservice...
    start "GenBank OTP Microservice (Port 8000)" /min cmd /c "cd /d "%~dp0Auth-Service" && "%PY_CMD%" -m uvicorn main:app --host 127.0.0.1 --port 8000"
    timeout /t 1 /nobreak >nul
    echo       FastAPI Microservice is now ONLINE on port 8000.
) else (
    echo       FastAPI Microservice is already ONLINE on port 8000.
)

:: 3. Fast Spring Boot NetBanking Platform boot
echo.
echo [3/3] Starting Spring Boot NetBanking Platform...
echo ======================================================================
echo   GenBank Web Portal URL : http://localhost:8080/
echo   CBS Admin Terminal     : http://localhost:8080/ (CBS Admin tab)
echo   FastAPI OTP Service    : http://127.0.0.1:8000/docs
echo ======================================================================
echo.
echo Launching default web browser the instant Spring Boot is ready...

:: Background browser launcher (polls in background and pops up immediately when port 8080 opens)
start "" cmd /c "for /l %%i in (1,1,40) do (netstat -ano | findstr ":8080 " >nul && start http://localhost:8080/ && exit || timeout /t 1 /nobreak >nul)"

:: Run Spring Boot with accelerated C1 tier compilation
set JVM_OPTS=-XX:+TieredCompilation -XX:TieredStopAtLevel=1 -Xms256m -Xmx768m
if defined DB_PASSWORD set JVM_OPTS=%JVM_OPTS% -DDB_PASSWORD="%DB_PASSWORD%" -Dspring.datasource.password="%DB_PASSWORD%"

if exist "target\GenBank-1.0.jar" (
    echo Launching fast-boot optimized binary: target\GenBank-1.0.jar
    java %JVM_OPTS% -jar target\GenBank-1.0.jar
) else (
    echo Launching via Maven Wrapper...
    call mvnw.cmd spring-boot:run
)

pause
