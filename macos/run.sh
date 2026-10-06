#!/bin/sh
# Run the shell with a debugger port on localhost:8001.
# It waits for the debugger to attach unless any argument is given.

. "$(dirname "$0")/env.sh"

debug="-agentlib:jdwp=transport=dt_socket,suspend=y,address=localhost:8001,server=y"
if [ $# -gt 0 ] ; then
	debug="-agentlib:jdwp=transport=dt_socket,suspend=n,address=localhost:8001,server=y"
fi

java $debug --enable-native-access=ALL-UNNAMED -Djava.library.path="$LIB_DIR" us.bringardner.shell.Console
