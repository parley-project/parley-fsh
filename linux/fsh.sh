#!/bin/sh
# Run the shell with a debugger port on localhost:8001, passing the arguments to it.
# Usage: fsh.sh [suspend] [shell arguments...]   (suspend: wait for the debugger to attach)

. "$(dirname "$0")/env.sh"

suspend='n'
if [ "$1" = "suspend" ]; then
	suspend='y'
	shift
fi

debug="-agentlib:jdwp=transport=dt_socket,suspend=$suspend,address=localhost:8001,server=y"

java $debug --enable-native-access=ALL-UNNAMED -Djava.library.path="$LIB_DIR" us.bringardner.fsh.Console "$@"
