@echo off
rem Builds the signed Android APK and publishes it where the dev server serves it
rem (backend\downloads\libero-shop.apk). See docs/mobile-android.md, sections 4 and 5.
rem Run from IntelliJ: configuration "APK Android (release)".
setlocal
cd /d "%~dp0"

if not exist "android\keystore.properties" (
    echo [ERREUR] android\keystore.properties est absent : l'APK ne serait pas signee.
    echo          Voir docs\mobile-android.md, section 3.3.
    exit /b 1
)

rem Gradle needs a JDK 21: the one IntelliJ downloaded, unless JAVA_HOME says otherwise.
if not defined JAVA_HOME (
    for /d %%J in ("%USERPROFILE%\.jdks\ms-21*") do set "JAVA_HOME=%%J"
)
if not defined JAVA_HOME (
    echo [ERREUR] JDK 21 introuvable : definissez JAVA_HOME.
    exit /b 1
)

echo === 1/3 Ecrans Angular + synchronisation Capacitor
call npm run mobile:sync || exit /b 1

echo === 2/3 APK signee
pushd android
call gradlew.bat assembleRelease || (popd & exit /b 1)
popd

set "APK=android\app\build\outputs\apk\release\app-release.apk"
if not exist "%APK%" (
    echo [ERREUR] %APK% n'a pas ete produite.
    exit /b 1
)

echo === 3/3 Publication
if not exist "..\backend\downloads" mkdir "..\backend\downloads"
copy /y "%APK%" "..\backend\downloads\libero-shop.apk" >nul || exit /b 1
echo APK publiee : backend\downloads\libero-shop.apk
endlocal
