@echo off
rem Run the shell from its packaged jar (target\fsh-*.jar: mvn package), passing the arguments to it.
rem (No AOT cache or fshexec here, as macos/fsh has: see compile.cmd.)

setlocal
for %%i in ("%~dp0.") do set LIB_DIR=%%~fi
for %%i in ("%LIB_DIR%\..") do set PROJECT_DIR=%%~fi
set cp_file=%PROJECT_DIR%\target\runtime-classpath.txt

set jar=
for /f "delims=" %%i in ('dir /b /o-d "%PROJECT_DIR%\target\fsh-*.jar" 2^>nul') do if not defined jar set jar=%PROJECT_DIR%\target\%%i
if not defined jar (
	pushd "%PROJECT_DIR%"
	call mvn -q package -DskipTests || (popd & exit /b 1)
	popd
	for /f "delims=" %%i in ('dir /b /o-d "%PROJECT_DIR%\target\fsh-*.jar"') do if not defined jar set jar=%PROJECT_DIR%\target\%%i
)
if not exist "%cp_file%" (
	pushd "%PROJECT_DIR%"
	call mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile="%cp_file%" || (popd & exit /b 1)
	popd
)
set deps=
for /f "usebackq delims=" %%i in ("%cp_file%") do set deps=%%i

rem (-Xss: deep recursion in scripts needs more than Java's 1 MB stacks; the serial collector with
rem a small young generation, its heap given back when it is not used; -XX:-MaxFDLimit, which
rem macos/fsh passes, is Unix-only: the JVM would not start with it here)
call "%~dp0java-options.cmd"
set opts=%NATIVE_ACCESS% -Djava.library.path="%LIB_DIR%" -Xss16m
set opts=%opts% -XX:+UseSerialGC -Xmn16m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20
set opts=%opts% -Djava.awt.headless=true

java %opts% -cp "%jar%;%deps%" us.bringardner.fsh.Console %*
