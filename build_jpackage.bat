@echo off
setlocal
echo ==========================================
echo   JPACKAGE CDIP e-Carta FTP Client
echo ==========================================

where mvn >nul 2>&1
if errorlevel 1 (
    echo [ERRO] Maven nao encontrado no PATH.
    exit /b 1
)

where jpackage >nul 2>&1
if errorlevel 1 (
    echo [ERRO] jpackage nao encontrado no PATH.
    exit /b 1
)

call mvn -q -DskipTests clean package
if errorlevel 1 (
    echo [ERRO] Falha no build Maven.
    exit /b %errorlevel%
)

if exist "dist" rmdir /s /q "dist"
mkdir dist

jpackage ^
  --type app-image ^
  --name "cdip-ecarta-ftp-client" ^
  --input "target" ^
  --main-jar "cdip-ecarta-ftp-client-all.jar" ^
  --dest "dist" ^
  --java-options "-Dfile.encoding=UTF-8" ^
  --vendor "Cesar Medeiros Correa"

if errorlevel 1 (
    echo [ERRO] Falha ao gerar app-image com jpackage.
    exit /b %errorlevel%
)

echo.
echo [OK] App-image gerada em:
echo   dist\cdip-ecarta-ftp-client
echo.
endlocal
exit /b 0
