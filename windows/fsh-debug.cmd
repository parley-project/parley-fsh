@echo off
rem Run the shell with a debugger port on localhost:8001, passing the arguments to it (fsh.sh's).
rem Usage: fsh-debug.cmd [suspend] [shell arguments...]   (suspend: wait for the debugger to attach)

setlocal
call "%~dp0env.cmd" || exit /b 1

set suspend=n
if "%~1"=="suspend" (
	set suspend=y
	shift
)

rem shift does not change %*: collect what is left
set args=
:args
if "%~1"=="" goto run
set args=%args% %1
shift
goto args

:run
set debug=-agentlib:jdwp=transport=dt_socket,suspend=%suspend%,address=localhost:8001,server=y

java %debug% --enable-native-access=ALL-UNNAMED -Djava.library.path="%LIB_DIR%" us.bringardner.fsh.Console %args%
