@echo off
echo ================================
echo AstralStream Quick Build
echo ================================
echo.

REM Set JAVA_HOME if not set
if "%JAVA_HOME%"=="" (
    echo Setting JAVA_HOME...
    set JAVA_HOME=C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot
)

echo Cleaning Gradle cache...
rd /s /q .gradle 2>nul
rd /s /q build 2>nul
rd /s /q app\build 2>nul

echo.
echo Building APK (this may take 3-5 minutes)...
call gradlew.bat assembleFreeDebug --no-daemon --no-build-cache

echo.
if exist app\build\outputs\apk\free\debug\app-free-debug.apk (
    echo ================================
    echo BUILD SUCCESSFUL!
    echo ================================
    echo.
    echo APK Location:
    echo app\build\outputs\apk\free\debug\app-free-debug.apk
    echo.
    echo Next steps:
    echo 1. Connect your phone via USB
    echo 2. Enable USB Debugging on your phone
    echo 3. Run: adb install app\build\outputs\apk\free\debug\app-free-debug.apk
) else (
    echo ================================
    echo BUILD FAILED
    echo ================================
    echo Please use Android Studio instead
)

pause