@echo off
setlocal
echo ==========================================
echo   Build CDIP e-Carta FTP Client
echo ==========================================

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Maven nao encontrado no PATH.
    exit /b 1
)

call mvn -q -DskipTests clean package
if errorlevel 1 (
    echo [ERRO] Falha no build Maven.
    exit /b %errorlevel%
)

echo.
echo [OK] Artefato gerado em:
echo   target\cdip-ecarta-ftp-client-all.jar
echo.
endlocal
exit /b 0
