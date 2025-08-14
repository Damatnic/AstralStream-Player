@echo off
echo Downloading Gradle Wrapper JAR...

REM Create gradle/wrapper directory if it doesn't exist
if not exist "gradle\wrapper" mkdir "gradle\wrapper"

REM Download gradle-wrapper.jar from the official Gradle repository
powershell -Command "& {Invoke-WebRequest -Uri 'https://github.com/gradle/gradle/raw/v8.7.0/gradle/wrapper/gradle-wrapper.jar' -OutFile 'gradle\wrapper\gradle-wrapper.jar'}"

if exist "gradle\wrapper\gradle-wrapper.jar" (
    echo Gradle wrapper JAR downloaded successfully!
) else (
    echo Failed to download gradle-wrapper.jar
    echo Please download it manually from: https://github.com/gradle/gradle/tree/v8.7.0/gradle/wrapper
)

pause