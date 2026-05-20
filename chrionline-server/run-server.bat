@echo off
cd /d "%~dp0"
set "FAT_JAR=target\chrionline-server-1.0-SNAPSHOT-jar-with-dependencies.jar"
if not exist "%~dp0mvnw.cmd" (
  echo mvnw.cmd not found. Install Maven and run: mvn package ^&^& java -jar %FAT_JAR%
  pause
  exit /b 1
)
echo Building server (fat JAR with dependencies^)...
call "%~dp0mvnw.cmd" package
if errorlevel 1 (
  echo Build failed.
  pause
  exit /b 1
)
if not exist "%FAT_JAR%" (
  echo Expected JAR not found: %FAT_JAR%
  pause
  exit /b 1
)
echo Starting ChriOnline socket server (default port 6000^)...
java -jar "%FAT_JAR%" %*
if errorlevel 1 pause
