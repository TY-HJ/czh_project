@echo off
title Repair-System Database Reset
echo ================================================
echo  This will RESET database repair_system
echo  (drop and recreate with demo data).
echo  MySQL must be running (root/1234).
echo ================================================
pause
"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql" -uroot -p1234 --default-character-set=utf8mb4 < "D:\Workbuddy\work_space\repair-system\sql\init.sql"
if %errorlevel%==0 (
  echo Database reset OK.
) else (
  echo Database reset FAILED. Check MySQL service.
)
pause
