# Sourced by fsh, fsh.sh and run.sh: NATIVE_ACCESS is --enable-native-access=ALL-UNNAMED when the
# java they run is 17 or later. Java 24 and later warn about JNI without it, and Java 11 (the oldest
# fsh runs on) does not start with it. The version comes from that JDK's release file, so no JVM is
# started to find it; when it can't be found the option is left out (at worst, that warning).

NATIVE_ACCESS=""
java_bin=$(command -v java 2>/dev/null)
java_home=""
if [ "$java_bin" = /usr/bin/java ] && [ -x /usr/libexec/java_home ]; then
	# (macOS's /usr/bin/java runs $JAVA_HOME's java, or the JDK java_home picks)
	java_home=${JAVA_HOME:-$(/usr/libexec/java_home 2>/dev/null)}
elif [ -n "$java_bin" ]; then
	java_home=$(cd "$(dirname "$(readlink -f "$java_bin" 2>/dev/null || echo "$java_bin")")/.." 2>/dev/null && pwd)
fi
java_major=$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$java_home/release" 2>/dev/null)
if [ -n "$java_major" ] && [ "$java_major" -ge 17 ]; then
	NATIVE_ACCESS="--enable-native-access=ALL-UNNAMED"
fi
