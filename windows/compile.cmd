@echo off
rem Build nativekeyboard.dll for us.bringardner.fsh.NativeKeyboard (64-bit, like the JVM).
rem Run from this directory after `mvn compile` (the header is generated from the Java class),
rem in an "x64 Native Tools Command Prompt" for MSVC, or with MinGW-w64's g++ on the PATH.
rem
rem There is no fshexec on Windows: what it does (process groups, signals, descriptors above 2,
rem a subshell's stand-in) has no Windows equivalent, and fsh runs without it.

setlocal
cd /d "%~dp0"
if "%JAVA_HOME%"=="" (
	echo JAVA_HOME is not set
	exit /b 1
)
set idir=%JAVA_HOME%\include
set name=NativeKeyboard

rem regenerate the JNI header so the function names always match the Java package
rem (javac's classes go in the project's target, not the system's temporary directory)
set classes=..\target\jni-classes
if not exist "%classes%" mkdir "%classes%"
set cpfile=..\target\jni-classpath.txt
pushd ..
call mvn -q dependency:build-classpath -Dmdep.outputFile=target\jni-classpath.txt
if errorlevel 1 (
	popd
	exit /b 1
)
popd
set deps=
for /f "usebackq delims=" %%i in ("%cpfile%") do set deps=%%i
"%JAVA_HOME%\bin\javac" -h . -d "%classes%" ..\src\main\java\us\bringardner\fsh\%name%.java -cp "..\target\classes;%deps%"
if errorlevel 1 exit /b 1
move /y us_bringardner_fsh_%name%.h %name%.h >nul

where cl >nul 2>nul
if not errorlevel 1 (
	cl /nologo /O2 /EHsc /LD /I"%idir%\win32" /I"%idir%" %name%.cpp /Fe:nativekeyboard.dll /link kernel32.lib
	if errorlevel 1 exit /b 1
	del %name%.obj nativekeyboard.exp nativekeyboard.lib 2>nul
	exit /b 0
)
where g++ >nul 2>nul
if not errorlevel 1 (
	g++ -O2 -shared -static-libgcc -static-libstdc++ -I"%idir%\win32" -I"%idir%" %name%.cpp -o nativekeyboard.dll
	exit /b %errorlevel%
)
echo No C++ compiler: run this from an "x64 Native Tools Command Prompt", or put MinGW-w64 on the PATH
exit /b 1
