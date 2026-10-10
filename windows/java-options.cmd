@echo off
rem Called by fsh.cmd, run.cmd and fsh-debug.cmd: NATIVE_ACCESS is --enable-native-access=ALL-UNNAMED
rem when the java they run is 17 or later. Java 24 and later warn about JNI without it, and Java 11
rem (the oldest fsh runs on) does not start with it. The version comes from that JDK's release file;
rem when it can't be found (a javapath stub, for one) the option is left out (at worst, that warning).

set NATIVE_ACCESS=
set java_home=
set java_major=
for /f "delims=" %%j in ('where java 2^>nul') do if not defined java_home for %%h in ("%%~dpj..") do set java_home=%%~fh
if not defined java_home goto :eof
if not exist "%java_home%\release" goto :eof
for /f "tokens=2 delims==" %%v in ('findstr /b "JAVA_VERSION=" "%java_home%\release"') do for /f "tokens=1 delims=." %%m in (%%v) do set java_major=%%m
if not defined java_major goto :eof
if %java_major% GEQ 17 set NATIVE_ACCESS=--enable-native-access=ALL-UNNAMED
