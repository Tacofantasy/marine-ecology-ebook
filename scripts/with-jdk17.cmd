@echo off
setlocal

for %%I in ("%~dp0..") do set "PROJECT_ROOT=%%~fI"
for /d %%D in ("%PROJECT_ROOT%\.tools\jdk17\*") do (
    if exist "%%~fD\bin\java.exe" set "JAVA_HOME=%%~fD"
)

if not defined JAVA_HOME (
    echo Project JDK 17 was not found under "%PROJECT_ROOT%\.tools\jdk17".
    exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
%*
exit /b %ERRORLEVEL%
