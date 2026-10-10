@echo off
rem Called by run.cmd and fsh-debug.cmd: sets CLASSPATH and LIB_DIR for running the shell from this project.
rem The dependency classpath comes from Maven and is cached in target\ (delete it after changing pom.xml).

for %%i in ("%~dp0.") do set LIB_DIR=%%~fi
for %%i in ("%LIB_DIR%\..") do set PROJECT_DIR=%%~fi
set cp_file=%PROJECT_DIR%\target\runtime-classpath.txt

if not exist "%PROJECT_DIR%\target\classes" (
	pushd "%PROJECT_DIR%"
	call mvn -q compile || (popd & exit /b 1)
	popd
)
if not exist "%cp_file%" (
	pushd "%PROJECT_DIR%"
	call mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile="%cp_file%" || (popd & exit /b 1)
	popd
)

set deps=
for /f "usebackq delims=" %%i in ("%cp_file%") do set deps=%%i
set CLASSPATH=%PROJECT_DIR%\target\classes;%deps%
call "%~dp0java-options.cmd"
