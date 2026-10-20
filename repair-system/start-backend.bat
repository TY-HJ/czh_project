@echo off
title Repair-System Backend (8080)
cd /d D:\Workbuddy\work_space\repair-system\backend
echo ============================================
echo  Starting backend: http://localhost:8080
echo  Close this window to stop the backend.
echo ============================================
java -jar target\repair-system-1.0.0.jar --server.port=8080
pause
