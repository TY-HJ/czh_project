@echo off
title Repair-System One-Click Start
echo ================================================
echo  Starting backend and frontend, please wait...
echo  Browser will open http://localhost:5173
echo  Test accounts (password 123456):
echo    admin    - ADMIN
echo    zhangsan - RESIDENT
echo    lisi     - WORKER
echo    wangwu   - WORKER
echo ================================================
start "Repair-Backend" cmd /k "cd /d D:\Workbuddy\work_space\repair-system\backend && java -jar target\repair-system-1.0.0.jar --server.port=8080"
start "Repair-Frontend" cmd /k "cd /d D:\Workbuddy\work_space\repair-system\frontend && call npm run dev"
timeout /t 10 >nul
start http://localhost:5173
echo Two windows opened (backend + frontend).
echo Do NOT close them during the demo.
