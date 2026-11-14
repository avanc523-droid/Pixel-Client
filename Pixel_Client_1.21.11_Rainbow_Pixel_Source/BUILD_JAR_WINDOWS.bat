@echo off
setlocal
where java >nul 2>nul || (echo Java 21 is required. & pause & exit /b 1)
where gradle >nul 2>nul || (echo Gradle 9.2.1 is required. See README for GitHub Actions alternative. & pause & exit /b 1)
call gradle clean build --no-daemon
if errorlevel 1 (echo Build failed. Check the output for details. & pause & exit /b 1)
echo Built: build\libs\pixelclient-1.0.0.jar
pause
