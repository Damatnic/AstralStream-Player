@echo off
echo Attempting quick build with error suppression...

REM Kill any existing Gradle daemons
call gradlew.bat --stop

REM Clean previous builds
call gradlew.bat clean

REM Try to build with leniency flags
call gradlew.bat assembleFreeDebug -x lint -x test --continue --stacktrace

echo.
echo Build attempt complete. Check for APK in:
echo app\build\outputs\apk\free\debug\
pause