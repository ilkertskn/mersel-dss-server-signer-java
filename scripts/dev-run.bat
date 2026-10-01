@echo off
REM =====================================================================
REM  dev-run.bat — Mersel DSS Signer'i IDE'siz calistirmak icin Windows scripti.
REM =====================================================================
REM  Kullanim:
REM    scripts\dev-run.bat                  ^> default (KURUM17 EC-P384 PFX)
REM    scripts\dev-run.bat kurum02-ec384    ^> KURUM02 EC-P384 PFX
REM    scripts\dev-run.bat mali-muhur-akis  ^> Mali Muhur AKIS (Windows)
REM    scripts\dev-run.bat list-hsm         ^> HSM sertifikalarini --force-login ile listele
REM    scripts\dev-run.bat list             ^> mevcut profilleri listele
REM
REM  Mali Muhur senaryolarinda CERTIFICATE_PIN env'i zorunlu:
REM    set CERTIFICATE_PIN=1234
REM    scripts\dev-run.bat mali-muhur-akis
REM =====================================================================

setlocal enabledelayedexpansion

set "SCENARIO=%~1"
if "%SCENARIO%"=="" set "SCENARIO=default"

REM ----- list mode
if /I "%SCENARIO%"=="list" (
    echo Mevcut senaryolar:
    echo   default ^| kurum17-ec384     -^> testkurum17 EC-P384 ^(PIN=328829^) [default]
    echo   kurum02-ec384                -^> testkurum02 EC-P384       ^(PIN=825095^)
    echo   kurum03-ec384                -^> testkurum03 EC-P384       ^(PIN=540425^)
    echo   mali-muhur-akis ^| mali-muhur -^> Mali Muhur AKIS server'i baslat ^(Windows^)
    echo   list-hsm ^| list-certs-hsm    -^> HSM sertifikalarini --force-login ile LISTELE
    exit /b 0
)

REM ----- HSM listeleme modu (--list-certificates --force-login)
REM Spring KALKMAZ; SignatureApplication.listCertificates() Spring profile /
REM local-secrets.properties okumaz, sadece env var'lari okur. AKIS gibi public
REM objeleri login'siz gostermeyen HSM'ler icin --force-login token sorgusundan
REM once C_Login yapar.
if /I "%SCENARIO%"=="list-hsm"       goto :list_hsm
if /I "%SCENARIO%"=="list-certs-hsm" goto :list_hsm
goto :after_list_hsm

:list_hsm
if "%CERTIFICATE_PIN%"=="" (
    echo [HATA] HSM listeleme icin CERTIFICATE_PIN env'i gerekli.
    echo        Ornek: set CERTIFICATE_PIN=1234 ^&^& scripts\dev-run.bat list-hsm
    exit /b 1
)
REM Disaridan verilmediyse AKIS Windows default'unu kullan.
if "%PKCS11_LIBRARY%"==""       set "PKCS11_LIBRARY=C:/Windows/System32/akisp11.dll"
if "%PKCS11_SLOT%"==""          set "PKCS11_SLOT=-1"
if "%PKCS11_SLOT_LIST_INDEX%"=="" set "PKCS11_SLOT_LIST_INDEX=-1"
if "%PKCS11_NULL_INIT_ARGS%"=="" set "PKCS11_NULL_INIT_ARGS=false"
set "PKCS11_FORCE_LOGIN=true"

echo ^> OS         : windows
echo ^> Senaryo    : list-hsm ^(--list-certificates --force-login^)
echo ^> Library    : %PKCS11_LIBRARY%
echo ^> Force login: aktif

where java >nul 2>&1
if errorlevel 1 (
    echo [HATA] java bulunamadi - JDK 8+ kurulu olmali.
    exit /b 1
)

set "MVN_CMD=mvn"
if exist ".\mvnw.cmd" set "MVN_CMD=.\mvnw.cmd"
where %MVN_CMD% >nul 2>&1
if errorlevel 1 (
    if not exist ".\mvnw.cmd" (
        echo [HATA] mvn ^(veya .\mvnw.cmd^) bulunamadi - Maven 3.6+ kurulu olmali.
        exit /b 1
    )
)
echo [OK] HSM sertifikalari listeleniyor...
%MVN_CMD% -q exec:java -Dexec.mainClass=io.mersel.dss.signer.api.SignatureApplication -Dexec.args="--list-certificates --force-login"
exit /b %ERRORLEVEL%

:after_list_hsm

REM ----- scenario -> profile mapping
if /I "%SCENARIO%"=="default"          set "PROFILES=local,pfx-kurum17-ec384"
if /I "%SCENARIO%"=="kurum17-ec384"    set "PROFILES=local,pfx-kurum17-ec384"
if /I "%SCENARIO%"=="kurum02-ec384"    set "PROFILES=local,pfx-kurum02-ec384"
if /I "%SCENARIO%"=="kurum03-ec384"    set "PROFILES=local,pfx-kurum03-ec384"
if /I "%SCENARIO%"=="mali-muhur-akis"  set "PROFILES=local,mali-muhur-akis-windows"
if /I "%SCENARIO%"=="mali-muhur"       set "PROFILES=local,mali-muhur-akis-windows"

if not defined PROFILES (
    echo [HATA] Bilinmeyen senaryo: %SCENARIO%
    echo Liste icin: scripts\dev-run.bat list
    exit /b 1
)

echo ^> OS         : windows
echo ^> Senaryo    : %SCENARIO%
echo ^> Profiles   : %PROFILES%

REM ----- Java check
where java >nul 2>&1
if errorlevel 1 (
    echo [HATA] java bulunamadi - JDK 8+ kurulu olmali.
    exit /b 1
)
for /f "tokens=*" %%v in ('java -version 2^>^&1 ^| findstr /R "version"') do (
    echo ^> Java       : %%v
    goto :java_ok
)
:java_ok

REM ----- Mali Muhur senaryosunda PIN fail-fast
echo %PROFILES% | findstr /C:"mali-muhur-akis" >nul
if not errorlevel 1 (
    if "%CERTIFICATE_PIN%"=="" (
        echo [HATA] Mali Muhur senaryosu icin CERTIFICATE_PIN env'i gerekli.
        echo        Ornek: set CERTIFICATE_PIN=1234 ^&^& scripts\dev-run.bat mali-muhur-akis
        exit /b 1
    )
)

REM ----- Maven runner
set "MVN_CMD=mvn"
if exist ".\mvnw.cmd" set "MVN_CMD=.\mvnw.cmd"

where %MVN_CMD% >nul 2>&1
if errorlevel 1 (
    if not exist ".\mvnw.cmd" (
        echo [HATA] mvn ^(veya .\mvnw.cmd^) bulunamadi - Maven 3.6+ kurulu olmali.
        exit /b 1
    )
)
echo ^> Maven      : %MVN_CMD%
echo [OK] Baslatiliyor...

%MVN_CMD% spring-boot:run -Dspring-boot.run.profiles=%PROFILES%

endlocal
