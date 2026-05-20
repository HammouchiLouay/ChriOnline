@echo off
cd /d "%~dp0"
echo Starting JavaFX client (Maven Wrapper: mvnw javafx:run)...
if exist "%~dp0mvnw.cmd" (
  call "%~dp0mvnw.cmd" javafx:run
) else (
  echo mvnw.cmd not found. Restore it from the repo or install Maven and run: mvn javafx:run
  pause
  exit /b 1
)
if errorlevel 1 pause
