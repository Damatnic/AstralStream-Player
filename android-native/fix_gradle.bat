@echo off
echo ========================================
echo Fixing Gradle Lock Issues
echo ========================================
echo.

echo Step 1: Killing all Java processes...
taskkill /F /IM java.exe 2>nul
taskkill /F /IM javaw.exe 2>nul

echo.
echo Step 2: Stopping Gradle daemons...
call gradlew.bat --stop

echo.
echo Step 3: Deleting Gradle cache locks...
del /F /Q "%USERPROFILE%\.gradle\caches\journal-1\*.lock" 2>nul
del /F /Q "%USERPROFILE%\.gradle\caches\*.lock" 2>nul

echo.
echo Step 4: Removing problematic cache folder...
rd /S /Q "%USERPROFILE%\.gradle\caches\journal-1" 2>nul

echo.
echo Step 5: Clear daemon folder...
rd /S /Q "%USERPROFILE%\.gradle\daemon" 2>nul

echo.
echo ========================================
echo Gradle issues fixed!
echo ========================================
echo.
echo Now you can:
echo 1. Open Android Studio
echo 2. Click "Sync Project with Gradle Files"
echo 3. Click Run to build and install
echo.
pause