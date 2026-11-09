#!/usr/bin/env bash
# ======================================================================
# GenBank (Apna Bank) - 1-Click Platform Launcher for Linux / macOS / Cloud
# ======================================================================
set -e

echo "=========================================================="
echo " Starting GenBank Digital Core Banking Platform"
echo "=========================================================="

# Check if Docker Compose is available
if command -v docker &> /dev/null && docker compose version &> /dev/null; then
    echo "[Mode: Docker] Launching all services with Docker Compose..."
    docker compose up --build -d
    echo ""
    echo "=========================================================="
    echo " GenBank is running in Docker!"
    echo " Web Portal:  http://localhost:8080"
    echo " Auth Docs:   http://localhost:8000/docs"
    echo "=========================================================="
    exit 0
fi

# Fallback: Native Bash Execution
echo "[Mode: Native] Starting Python FastAPI OTP microservice on port 8000..."
cd Auth-Service
python3 -m pip install -r requirements.txt
python3 main.py &
PID_AUTH=$!
cd ..

echo "Starting Spring Boot core banking platform on port 8080..."
./mvnw spring-boot:run &
PID_BOOT=$!

trap "echo 'Shutting down GenBank...'; kill $PID_AUTH $PID_BOOT 2>/dev/null || true" SIGINT SIGTERM EXIT
wait
