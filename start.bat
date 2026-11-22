@echo off
chcp 65001 >nul
setlocal EnableExtensions
title YingYou 一键启动

set "ROOT=%~dp0"
set "BACKEND=%ROOT%backend"
set "FRONTEND=%ROOT%frontend"
set "ENV_FILE=%ROOT%.env"

echo ============================================
echo   YingYou 一键启动
echo ============================================
echo.

REM ---------- 0. 基础文件检查 ----------
if not exist "%BACKEND%\mvnw.cmd" goto noMvnw
if not exist "%FRONTEND%\package.json" goto noFrontend

REM ---------- 1. 读取 .env ----------
set "DB_PASSWORD="
if not exist "%ENV_FILE%" goto noEnv
for /f "usebackq eol=# tokens=1,* delims==" %%A in ("%ENV_FILE%") do (
    if /I "%%A"=="DB_PASSWORD" set "DB_PASSWORD=%%B"
    if /I "%%A"=="DB_URL" set "DB_URL=%%B"
    if /I "%%A"=="DB_USER" set "DB_USER=%%B"
)
if not defined DB_PASSWORD goto noPassword

REM ---------- 2. 定位 JDK ----------
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" goto javaReady
set "JAVA_HOME="
for /d %%J in ("C:\Program Files\Microsoft\jdk-21*") do if not defined JAVA_HOME set "JAVA_HOME=%%~fJ"
if not defined JAVA_HOME (
    for /d %%J in ("%TEMP%\yingyou-tools\jdk21\jdk-*") do if not defined JAVA_HOME set "JAVA_HOME=%%~fJ"
)
if not defined JAVA_HOME (
    for /f "delims=" %%J in ('where java') do if not defined JAVA_HOME set "JAVA_HOME=%%~dpJ.."
)
if not defined JAVA_HOME goto noJava
if not exist "%JAVA_HOME%\bin\java.exe" goto noJava
:javaReady
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo [OK] JAVA_HOME = %JAVA_HOME%

REM ---------- 3. 定位 npm ----------
set "NPM_CMD="
for /f "delims=" %%N in ('where npm.cmd') do if not defined NPM_CMD set "NPM_CMD=%%N"
if not defined NPM_CMD goto noNode
echo [OK] npm = %NPM_CMD%

REM ---------- 4. 端口检查 ----------
call :portOpen 5432
if errorlevel 1 goto noDatabase

call :portOpen 8080
if not errorlevel 1 (
    echo [SKIP] 端口 8080 已被占用，跳过启动后端。
    set "SKIP_BACKEND=1"
)

call :portOpen 5173
if not errorlevel 1 (
    echo [SKIP] 端口 5173 已被占用，跳过启动前端。
    set "SKIP_FRONTEND=1"
)

REM ---------- 5. 启动后端 ----------
if defined SKIP_BACKEND goto backendDone
echo [START] 后端：%BACKEND%
start "YingYou Backend" /d "%BACKEND%" cmd /k "call mvnw.cmd spring-boot:run"

echo [WAIT] 等待后端 /actuator/health 就绪（最多 90 秒）...
powershell -NoProfile -Command "$ok=$false; for($i=0;$i -lt 90;$i++){ try { $r=Invoke-WebRequest -UseBasicParsing -Uri 'http://localhost:8080/actuator/health' -TimeoutSec 2; if($r.StatusCode -eq 200){$ok=$true;break} } catch {}; Start-Sleep -Seconds 1 }; if($ok){exit 0}else{exit 1}"
if errorlevel 1 (
    echo [WARN] 后端 90 秒内未就绪，请查看 "YingYou Backend" 窗口的日志。
) else (
    echo [OK] 后端已就绪：http://localhost:8080
)
:backendDone

REM ---------- 6. 启动前端 ----------
if defined SKIP_FRONTEND goto frontendDone
if not exist "%FRONTEND%\node_modules" (
    echo [INSTALL] 首次运行，正在安装前端依赖...
    pushd "%FRONTEND%"
    call npm.cmd install
    popd
    if errorlevel 1 goto npmFail
)
echo [START] 前端：%FRONTEND%
start "YingYou Frontend" /d "%FRONTEND%" cmd /k "call npm.cmd run dev"

echo [WAIT] 等待前端 5173 就绪（最多 30 秒）...
powershell -NoProfile -Command "for($i=0;$i -lt 30;$i++){ if ((Test-NetConnection -ComputerName 127.0.0.1 -Port 5173 -InformationLevel Quiet) -eq $true){exit 0}; Start-Sleep -Seconds 1 }; exit 1"
if errorlevel 1 (
    echo [WARN] 前端 30 秒内未就绪，请查看 "YingYou Frontend" 窗口的日志。
) else (
    echo [OK] 前端已就绪：http://localhost:5173
)
:frontendDone

REM ---------- 7. 打开浏览器 ----------
start "" "http://localhost:5173"
echo.
echo 启动流程已结束。后端/前端窗口请保持打开；关闭窗口即停止对应服务。
ping -n 6 127.0.0.1 >nul 2>&1
exit /b 0

REM ---------- 辅助函数：检查端口 ----------
:portOpen
powershell -NoProfile -Command "if ((Test-NetConnection -ComputerName 127.0.0.1 -Port %~1 -InformationLevel Quiet) -eq $true) { exit 0 } else { exit 1 }"
exit /b %ERRORLEVEL%

REM ---------- 错误处理 ----------
:noMvnw
echo [ERROR] 未找到 backend\mvnw.cmd，请确认项目结构完整。
goto fail

:noFrontend
echo [ERROR] 未找到 frontend\package.json，请确认项目结构完整。
goto fail

:noEnv
echo [ERROR] 未找到根目录 .env 文件。
echo         请在项目根目录创建 .env，内容至少包含：DB_PASSWORD=你的数据库密码
goto fail

:noPassword
echo [ERROR] .env 中未设置 DB_PASSWORD。
echo         示例：DB_PASSWORD=123456
goto fail

:noJava
echo [ERROR] 未找到可用的 JDK 21。
echo         请安装 JDK 21 并加入 PATH，或确保临时目录仍有 yingyou-tools\jdk21\jdk-*。
goto fail

:noNode
echo [ERROR] 未找到 npm.cmd，请先安装 Node.js 并加入 PATH。
goto fail

:noDatabase
echo [ERROR] 本机 127.0.0.1:5432 无法连接。
echo         请先启动 PostgreSQL，并确认 yingyou 库/角色存在（密码见 .env）。
goto fail

:npmFail
echo [ERROR] 前端依赖安装失败，请查看上方 npm 输出。
goto fail

:fail
echo.
echo 启动失败，请按提示处理后重试。
pause
exit /b 1
