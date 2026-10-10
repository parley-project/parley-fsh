@echo off
rem Run the shell with a debugger port on localhost:8001.
rem It waits for the debugger to attach unless any argument is given.

setlocal
call "%~dp0env.cmd" || exit /b 1

set debug=-agentlib:jdwp=transport=dt_socket,suspend=y,address=localhost:8001,server=y
if not "%~1"=="" set debug=-agentlib:jdwp=transport=dt_socket,suspend=n,address=localhost:8001,server=y

java %debug% --enable-native-access=ALL-UNNAMED -Djava.library.path="%LIB_DIR%" us.bringardner.fsh.Console
