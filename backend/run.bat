@echo off
rem Compile and start the CarePlus backend (needs JDK 11+ : javac and java on PATH)
cd /d "%~dp0"
if not exist out mkdir out
javac -d out src\com\careplus\*.java || exit /b 1
java -cp out com.careplus.Main 8080 ..\frontend
