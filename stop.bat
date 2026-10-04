@echo off
title Stop GenBank Platform
echo ======================================================================
echo                     STOPPING GENBANK SERVICES                         
echo ======================================================================
echo.

:: Stop Python FastAPI process on port 8000
echo Stopping FastAPI OTP Microservice (port 8000)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-NetTCPConnection -LocalPort 8000 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique; if ($p) { Stop-Process -Id $p -Force; Write-Host '  -> Stopped FastAPI OTP service.' } else { Write-Host '  -> FastAPI was not running.' }"

:: Stop Spring Boot process on port 8080
echo Stopping Spring Boot NetBanking Platform (port 8080)...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$p = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique; if ($p) { Stop-Process -Id $p -Force; Write-Host '  -> Stopped Spring Boot app.' } else { Write-Host '  -> Spring Boot was not running.' }"

echo.
echo All GenBank application processes have been stopped cleanly.
echo ======================================================================
timeout /t 3 >nul
