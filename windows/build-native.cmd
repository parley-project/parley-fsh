@echo off
rem Build fsh as a native image (GraalVM 21 or later): target\native\fsh.exe, with
rem nativekeyboard.dll next to it (it finds the library there, and fshexec has no Windows
rem equivalent). Run windows\compile.cmd first, in an "x64 Native Tools Command Prompt" (the
rem native image needs the MSVC build tools too).
rem
rem GRAALVM_HOME (or JAVA_HOME) names the GraalVM. Its settings are in
rem src\main\resources\META-INF\native-image\us.bringardner\fsh.

setlocal
for %%i in ("%~dp0.") do set LIB_DIR=%%~fi
for %%i in ("%LIB_DIR%\..") do set PROJECT_DIR=%%~fi
cd /d "%PROJECT_DIR%" || exit /b 1

set graal=%GRAALVM_HOME%
if not defined graal if defined JAVA_HOME if exist "%JAVA_HOME%\bin\native-image.cmd" set graal=%JAVA_HOME%
if not defined graal (
	echo build-native.cmd: no GraalVM native-image ^(set GRAALVM_HOME^)
	exit /b 1
)
if not exist "%graal%\bin\native-image.cmd" (
	echo build-native.cmd: no native-image in %graal%
	exit /b 1
)
if not exist "%LIB_DIR%\nativekeyboard.dll" (
	echo build-native.cmd: windows\nativekeyboard.dll is not there: run windows\compile.cmd first
	exit /b 1
)

call mvn -q package -DskipTests || exit /b 1
call mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target\runtime-classpath.txt || exit /b 1

rem (the jar and its dependencies in one folder, so the class path is a wildcard: a Windows
rem command line is limited, and the dependencies' paths are long)
if exist target\native-libs rmdir /s /q target\native-libs
mkdir target\native-libs target\native target\native-tmp target\native-config
set jar=
for /f "delims=" %%i in ('dir /b /o-d target\fsh-*.jar') do if not defined jar set jar=target\%%i
copy /y "%jar%" target\native-libs\ >nul || exit /b 1
powershell -NoProfile -Command "(Get-Content target\runtime-classpath.txt -Raw).Trim() -split ';' | Where-Object { $_ } | ForEach-Object { Copy-Item $_ target\native-libs\ -Force }" || exit /b 1

rem (all of fsh's and parley's classes for reflection, besides what tools\aot-training.sh used)
powershell -NoProfile -Command "$jars = Get-ChildItem target\native-libs\*.jar | Where-Object { $_.Name -match 'fsh-|parley' } | ForEach-Object { $_.FullName }; & python tools\native-reflect.py @jars target\native-config\reflect-config.json" || exit /b 1

rem (its work files in target, not the system's temporary directory)
set TMP=%PROJECT_DIR%\target\native-tmp
set TEMP=%TMP%
call "%graal%\bin\native-image.cmd" -cp "target\native-libs\*" ^
	-H:TempDirectory="%PROJECT_DIR%\target\native-tmp" -H:ConfigurationFileDirectories=target\native-config ^
	-Dfsh.libdir="%PROJECT_DIR%\target\native" -o target\native\fsh us.bringardner.fsh.Console || exit /b 1
copy /y "%LIB_DIR%\nativekeyboard.dll" target\native\ >nul || exit /b 1
echo target\native\fsh.exe
