@echo off
title Repair-System Frontend (5173)
cd /d D:\Workbuddy\work_space\repair-system\frontend
echo ============================================
echo  Starting frontend: http://localhost:5173
echo  Close this window to stop the frontend.
echo ============================================
call npm run dev
pause
