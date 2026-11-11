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

:: 1. Check if MySQL is running on port 3306
echo [1/3] Checking MySQL Database on port 3306...
powershell -NoProfile -Command "$c = New-Object System.Net.Sockets.TcpClient; try { $c.Connect('127.0.0.1', 3306); exit 0 } catch { exit 1 } finally { $c.Dispose() }" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       MySQL is stopped. Starting MySQL Server daemon...
    start "GenBank MySQL Server" /min "D:\Download\mysql-enterprise-9.7.1_winx64_bundle\mysql-commercial-9.7.1-winx64\mysql-commercial-9.7.1-winx64\bin\mysqld.exe" --defaults-file="D:\Download\mysql-enterprise-9.7.1_winx64_bundle\mysql-commercial-9.7.1-winx64\mysql-commercial-9.7.1-winx64\my.ini" --console
    echo       Waiting for MySQL to finish initializing...
    powershell -NoProfile -Command "for ($i=0; $i -lt 15; $i++) { $c = New-Object System.Net.Sockets.TcpClient; try { $c.Connect('127.0.0.1', 3306); exit 0 } catch { Start-Sleep -Seconds 1 } finally { $c.Dispose() } }; exit 1" >nul 2>&1
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
powershell -NoProfile -Command "$c = New-Object System.Net.Sockets.TcpClient; try { $c.Connect('127.0.0.1', 8000); exit 0 } catch { exit 1 } finally { $c.Dispose() }" >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo       Starting Python OTP Microservice...
    start "GenBank OTP Microservice (Port 8000)" cmd /k "cd /d "%~dp0Auth-Service" && "%PY_CMD%" -m uvicorn main:app --host 127.0.0.1 --port 8000"
    echo       Waiting for OTP Microservice to initialize...
    powershell -NoProfile -Command "for ($i=0; $i -lt 15; $i++) { $c = New-Object System.Net.Sockets.TcpClient; try { $c.Connect('127.0.0.1', 8000); exit 0 } catch { Start-Sleep -Seconds 1 } finally { $c.Dispose() } }; exit 1" >nul 2>&1
    if %ERRORLEVEL% EQU 0 (
        echo       FastAPI Microservice is now ONLINE on port 8000.
    ) else (
        echo       WARNING: FastAPI Microservice could not be reached on port 8000.
    )
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
set JVM_OPTS=
if defined DB_PASSWORD set JVM_OPTS=-DDB_PASSWORD="%DB_PASSWORD%" -Dspring.datasource.password="%DB_PASSWORD%"

if exist "target\GenBank-1.0.jar" (
    echo Launching optimized binary: target\GenBank-1.0.jar
    java %JVM_OPTS% -jar target\GenBank-1.0.jar
) else (
    echo Launching via Maven Wrapper...
    call mvnw.cmd spring-boot:run
)

pause
