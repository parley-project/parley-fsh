#!/bin/sh
# Package the jlink build (tools/build-jlink.sh: target/jlink/fsh) with jpackage, as an installer
# for this platform, in dist/:
#
#   macOS:  pkg (default; installs /Applications/fsh.app), dmg, app-image (fsh.app)
#   Linux:  deb (default; installs /opt/fsh), rpm, app-image (a folder)
#
# Usage: tools/build-jpackage.sh [TYPE] [VERSION]     VERSION: the pom's, without -SNAPSHOT
#
# The command is jpackage's launcher (fsh.app/Contents/MacOS/fsh, /opt/fsh/bin/fsh), with the
# runtime, fsh.jar, the keyboard library and fshexec in the package. Link it from a directory on
# PATH. jpackage's launcher starts the JVM in its own process, so unlike target/jlink/fsh/bin/fsh
# it cannot go through fshexec -j: signals ignored by the shell's caller are not seen as ignored
# (trap -p) when the JVM takes them first (QUIT, USR2). Everything else is the same.
#
# Needs: macOS, Xcode's command line tools; Linux, dpkg-deb and fakeroot (deb) or rpmbuild (rpm).
# The package is not signed (macOS: --mac-sign and a Developer ID, then notarize, for that).

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_DIR"

case $(uname -s) in
Darwin) platform=macos; lib=libnativekeyboard.dylib; type=${1:-pkg} ;;
Linux) platform=linux; lib=libnativekeyboard.so; type=${1:-deb} ;;
*) echo "$0: macOS and Linux only" >&2; exit 1 ;;
esac
version=${2:-$(awk -F'[<>]' '/<artifactId>fsh<\/artifactId>/ { getline; print $3; exit }' pom.xml)}
version=${version%-SNAPSHOT}
version=${version#v}

image=target/jlink/fsh
if [ ! -x "$image/bin/fsh" ] || [ pom.xml -nt "$image/bin/fsh" ] \
		|| [ -n "$(find src resources -newer "$image/bin/fsh" -type f 2>/dev/null | head -1)" ]; then
	tools/build-jlink.sh
fi
if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
	JAVA_HOME=$(/usr/libexec/java_home)
fi
jpackage=${JAVA_HOME:+$JAVA_HOME/bin/}jpackage
# (the runtime's version: jpackage must be from the same JDK)
runtime_major=$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$image/runtime/release")
jpackage_major=$("$jpackage" --version | sed 's/[^0-9].*//')
if [ "$runtime_major" != "$jpackage_major" ]; then
	echo "$0: the runtime is JDK $runtime_major, jpackage is JDK $jpackage_major: set JAVA_HOME to the JDK build-jlink.sh used" >&2
	exit 1
fi

# what goes in the app folder ($APPDIR): the jar, the library and fshexec (no class cache: its
# class path would not match the installed jar's)
input=target/jpackage/input
rm -rf target/jpackage
mkdir -p "$input" dist
cp -p "$image/lib/fsh.jar" "$image/lib/$lib" "$image/lib/fshexec" "$input/"

native_access=""
[ "$runtime_major" -ge 17 ] && native_access=--enable-native-access=ALL-UNNAMED

set -- --type "$type" --dest dist --name fsh --app-version "$version" \
	--vendor "Tony Bringardner" --copyright "Copyright 2004-$(date +%Y) Tony Bringardner" \
	--description "fsh, the FileSource Shell: a bash-like shell on any parley-files file system" \
	--input "$input" --main-jar fsh.jar --main-class us.bringardner.fsh.Console \
	--runtime-image "$image/runtime" \
	--java-options '-Djava.library.path=$APPDIR' \
	--java-options -Xss16m --java-options -XX:+UseSerialGC --java-options -Xmn16m \
	--java-options -XX:MinHeapFreeRatio=10 --java-options -XX:MaxHeapFreeRatio=20 \
	--java-options -XX:-MaxFDLimit --java-options -Djava.awt.headless=true \
	--temp target/jpackage/work
[ -n "$native_access" ] && set -- "$@" --java-options "$native_access"
if [ "$type" != app-image ]; then
	set -- "$@" --license-file LICENSE
fi
case $platform-$type in
macos-*)
	set -- "$@" --mac-package-identifier us.bringardner.fsh --mac-package-name fsh ;;
linux-app-image) ;;
linux-*)
	set -- "$@" --linux-package-name fsh --install-dir /opt \
		--linux-app-category utils ;;
esac
# (jpackage's work files kept in target: removed again here)
"$jpackage" "$@"
rm -rf target/jpackage/work
echo "dist: $(cd dist && ls -t | head -1)"
