@echo off
REM =============================================================================
REM AI Code Pilot - Windows Setup Script
REM =============================================================================
setlocal enabledelayedexpansion

echo.
echo ============================================================
echo   AI Code Pilot - Windows Setup
echo ============================================================
echo.

REM ── Check Java ───────────────────────────────────────────────────────────────
java -version >nul 2>&1
IF ERRORLEVEL 1 (
    echo [ERROR] Java not found. Download JDK 17+ from https://adoptium.net/
    pause & exit /b 1
)
echo [OK] Java detected
for /f "tokens=3" %%g in ('java -version 2^>^&1') do (
    echo       Version: %%g
    goto :java_done
)
:java_done

REM ── Check Maven ──────────────────────────────────────────────────────────────
mvn -version >nul 2>&1
IF ERRORLEVEL 1 (
    echo [WARN] Maven not found. Download from https://maven.apache.org/
    echo        Or use the Eclipse PDE build instead (see README).
)

REM ── Create model directory ───────────────────────────────────────────────────
SET MODEL_DIR=%USERPROFILE%\.aicodepilot\models
IF NOT EXIST "%MODEL_DIR%" (
    mkdir "%MODEL_DIR%"
    echo [OK] Created: %MODEL_DIR%
) ELSE (
    echo [OK] Model dir exists: %MODEL_DIR%
)

REM ── Model download instructions ───────────────────────────────────────────────
echo.
echo ── Download AI Model ─────────────────────────────────────
echo.
echo Download one of these models (choose based on your RAM):
echo.
echo   8 GB  RAM: DeepSeek-Coder 1.3B (recommended)
echo   URL: https://huggingface.co/TheBloke/deepseek-coder-1.3b-instruct-GGUF
echo   File: deepseek-coder-1.3b-instruct.Q4_K_M.gguf
echo.
echo   16 GB RAM: DeepSeek-Coder 6.7B (better quality)
echo   URL: https://huggingface.co/TheBloke/deepseek-coder-6.7B-instruct-GGUF
echo   File: deepseek-coder-6.7B-instruct.Q4_K_M.gguf
echo.
echo Save the .gguf file to: %MODEL_DIR%
echo.

REM ── llama.cpp instructions ────────────────────────────────────────────────────
echo ── Download llama.cpp (Windows binary) ───────────────────
echo.
echo   1. Go to: https://github.com/ggerganov/llama.cpp/releases
echo   2. Download: llama-bXXXX-bin-win-avx2-x64.zip
echo   3. Extract llama-cli.exe to: %USERPROFILE%\.aicodepilot\
echo.

REM ── Build with Maven ─────────────────────────────────────────────────────────
echo ── Building Plugin ───────────────────────────────────────
echo.
SET SCRIPT_DIR=%~dp0
SET PROJECT_DIR=%SCRIPT_DIR%..

cd /d "%PROJECT_DIR%"

IF EXIST "pom.xml" (
    mvn dependency:copy-dependencies -DoutputDirectory=lib -DincludeScope=runtime -q
    echo [OK] Dependencies downloaded to lib\
) ELSE (
    echo [WARN] pom.xml not found in %PROJECT_DIR%
)

REM ── Find Eclipse and install ─────────────────────────────────────────────────
echo.
echo ── Install Plugin to Eclipse ─────────────────────────────
echo.
SET /P ECLIPSE_DIR="Enter Eclipse installation directory (e.g. C:\eclipse): "

IF NOT EXIST "%ECLIPSE_DIR%\dropins" (
    mkdir "%ECLIPSE_DIR%\dropins"
)

REM Remove old version
del /q "%ECLIPSE_DIR%\dropins\com.aicodepilot_*.jar" 2>nul

REM Copy plugin JAR (if exists from Maven build)
IF EXIST "target\com.aicodepilot_*.jar" (
    copy "target\com.aicodepilot_*.jar" "%ECLIPSE_DIR%\dropins\"
    echo [OK] Plugin installed to: %ECLIPSE_DIR%\dropins\
) ELSE (
    echo [INFO] No JAR found. Use Eclipse PDE to run in dev mode.
    echo        File ^> Import ^> Existing Projects ^> Browse to: %PROJECT_DIR%
)

REM Copy lib JARs
IF EXIST "lib" (
    copy "lib\*.jar" "%ECLIPSE_DIR%\dropins\" >nul 2>&1
    echo [OK] Library JARs copied
)

echo.
echo ============================================================
echo   Setup Complete!
echo ============================================================
echo.
echo Next steps:
echo   1. Restart Eclipse  (or run: eclipse.exe -clean)
echo   2. Window ^> Show View ^> AI Code Pilot ^> AI Code Suggestions
echo   3. Window ^> Preferences ^> AI Code Pilot ^> Model Settings
echo      Model: %MODEL_DIR%\deepseek-coder-1.3b-instruct.Q4_K_M.gguf
echo      Binary: %USERPROFILE%\.aicodepilot\llama-cli.exe
echo.
pause
