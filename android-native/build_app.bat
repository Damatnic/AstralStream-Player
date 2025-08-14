@echo off
echo ====================================
echo AstralStream Build Script
echo ====================================
echo.

echo Stopping any existing Gradle daemons...
call gradlew.bat --stop

echo.
echo Cleaning previous builds...
call gradlew.bat clean

echo.
echo Building AstralStream Free Debug APK...
call gradlew.bat assembleFreeDebug

echo.
echo ====================================
echo Build Complete!
echo ====================================
echo.
echo APK Location:
echo app\build\outputs\apk\free\debug\app-free-debug.apk
echo.
echo To install on connected device:
echo adb install app\build\outputs\apk\free\debug\app-free-debug.apk
echo.
pause