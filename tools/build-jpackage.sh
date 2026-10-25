#!/bin/sh
# The macOS installer: dist/fsh-VERSION-macos-ARCH.dmg (and its .sha256), made with jpackage from
# the jlink build (tools/build-jlink.sh: target/jlink/fsh). The disk image holds fsh.app, to drag
# to Applications:
#
#   fsh.app/Contents/app/fsh        the fsh command (the jlink build's launcher): link it from a
#                                   directory on PATH (README.txt in the disk image says how)
#   fsh.app/Contents/MacOS/fsh      what the Finder starts: opens Terminal running Contents/app/fsh,
#                                   so fsh runs in a terminal (browse's full-screen browsers too)
#   fsh.app/Contents/app/fsh.jar    fsh, the keyboard library, fshexec, the class cache and the
#                                   plugins built in (plugins/: see tools/build-jlink.sh)
#   fsh.app/Contents/runtime        the Java runtime
#
# Its icon is macos/fsh.icns: Parley's tower with the >_ badge, the brand's icon for terminal
# apps (parley-brand's parley-icon-terminal-16.png ... -512.png, put together as one .icns).
#
# Usage: tools/build-jpackage.sh [TYPE] [VERSION]
#   TYPE     dmg (the default), pkg (installs /Applications/fsh.app) or app-image (fsh.app only)
#   VERSION  the pom's, without -SNAPSHOT
#
# jpackage makes fsh.app with a launcher of its own in Contents/MacOS, which would run fsh in its
# own window (not a terminal, where a full-screen browser can't run). That launcher is replaced by
# a script that opens Terminal on Contents/app/fsh, and the app is signed again (ad hoc), since the
# change breaks jpackage's signature. The disk image is not signed with a Developer ID or notarized
# (for that: codesign with a Developer ID, then notarytool). Linux: tools/build-linux-packages.sh; Windows:
# tools/build-msi.ps1.

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_DIR"

if [ "$(uname -s)" != Darwin ]; then
	echo "$0: macOS only (Linux: tools/build-linux-packages.sh; Windows: tools/build-msi.ps1)" >&2
	exit 1
fi
type=${1:-dmg}
case $type in
dmg|pkg|app-image) ;;
*) echo "usage: $0 [dmg|pkg|app-image] [VERSION]" >&2; exit 2 ;;
esac
version=${2:-$(awk -F'[<>]' '/<artifactId>fsh<\/artifactId>/ { getline; print $3; exit }' pom.xml)}
version=${version%-SNAPSHOT}
version=${version#v}
case $(uname -m) in
arm64) arch=aarch64 ;;
*) arch=$(uname -m) ;;
esac

image=target/jlink/fsh
if [ ! -x "$image/bin/fsh" ] || [ pom.xml -nt "$image/bin/fsh" ] || [ tools/build-jlink.sh -nt "$image/bin/fsh" ] \
		|| [ -n "$(find src resources -newer "$image/bin/fsh" -type f 2>/dev/null | head -1)" ]; then
	tools/build-jlink.sh
fi
if [ -z "$JAVA_HOME" ]; then
	JAVA_HOME=$(/usr/libexec/java_home)
fi
jpackage=$JAVA_HOME/bin/jpackage
# (the runtime's version: jpackage must be from the same JDK)
runtime_major=$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$image/runtime/release")
jpackage_major=$("$jpackage" --version | sed 's/[^0-9].*//')
if [ "$runtime_major" != "$jpackage_major" ]; then
	echo "$0: the runtime is JDK $runtime_major, jpackage is JDK $jpackage_major: set JAVA_HOME to the JDK build-jlink.sh used" >&2
	exit 1
fi

# what goes in the app folder ($APPDIR, Contents/app): all of the jlink build's lib, and its
# launcher as the command
# (absolute: jpackage passes its work folder to hdiutil as -mountroot, which a relative path fails)
work=$PROJECT_DIR/target/jpackage
rm -rf "$work"
mkdir -p "$work/input" "$work/dmg" dist
# (jpackage puts every jar in the input on the class path: fsh.jar and the plugins built in)
cp -Rp "$image"/lib/. "$work/input/"
cp -p "$image/bin/fsh" "$work/input/fsh"

cat > "$work/dmg/README.txt" <<TXT
fsh $version for macOS ($arch)

Drag fsh.app to Applications. Opened from the Finder, it opens a Terminal window running fsh.

To use fsh in a terminal, link its command from a directory on PATH, once:

    sudo mkdir -p /usr/local/bin
    sudo ln -sf /Applications/fsh.app/Contents/app/fsh /usr/local/bin/fsh

and to use it as your login shell, add it to /etc/shells and choose it:

    echo /usr/local/bin/fsh | sudo tee -a /etc/shells
    chsh -s /usr/local/bin/fsh

fsh.app is not notarized: macOS asks before opening it the first time (right-click it,
Open), or, once, in a terminal:

    xattr -dr com.apple.quarantine /Applications/fsh.app

Source and documentation: https://github.com/parley-project/parley-fsh
TXT

native_access=""
[ "$runtime_major" -ge 17 ] && native_access=--enable-native-access=ALL-UNNAMED

# (fsh.app first, always: its launcher is replaced (below), then the pkg is made from it, or the
# dmg by hdiutil create from a folder, as jpackage's own dmg needs hdiutil attach, which macOS can
# refuse)
set -- --type app-image --dest "$work/out" --name fsh --app-version "$version" \
	--vendor "Tony Bringardner" --copyright "Copyright 2004-$(date +%Y) Tony Bringardner" \
	--description "fsh, the FileSource Shell: a bash-like shell on any parley-files file system" \
	--input "$work/input" --main-jar fsh.jar --main-class us.bringardner.fsh.Console \
	--runtime-image "$image/runtime" \
	--java-options '-Djava.library.path=$APPDIR' \
	--java-options -Xss16m --java-options -XX:+UseSerialGC --java-options -Xmn16m \
	--java-options -XX:MinHeapFreeRatio=10 --java-options -XX:MaxHeapFreeRatio=20 \
	--java-options -XX:-MaxFDLimit \
	--mac-package-identifier us.bringardner.fsh --mac-package-name fsh --icon macos/fsh.icns \
	--temp "$work/temp"
[ -n "$native_access" ] && set -- "$@" --java-options "$native_access"
"$jpackage" "$@"

# (what the Finder starts: Terminal, running the fsh command in the app)
app=$work/out/fsh.app
cat > "$app/Contents/MacOS/fsh" <<'SH'
#!/bin/sh
# fsh.app opened from the Finder: fsh in a new Terminal window (the command is Contents/app/fsh)
exec open -a Terminal "$(cd "$(dirname "$0")/../app" && pwd)/fsh"
SH
chmod 755 "$app/Contents/MacOS/fsh"
codesign --force --deep --sign - "$app"

case $type in
dmg)
	# (the disk image: fsh.app, a link to Applications to drag it to, README.txt and LICENSE)
	mv "$work/out/fsh.app" "$work/dmg/"
	ln -s /Applications "$work/dmg/Applications"
	cp -p LICENSE "$work/dmg/"
	name=fsh-$version-macos-$arch.dmg
	rm -f "dist/$name"
	hdiutil create -quiet -volname "fsh $version" -srcfolder "$work/dmg" -fs HFS+ -format UDZO "dist/$name"
	(cd dist && shasum -a 256 "$name" > "$name.sha256")
	echo "$PROJECT_DIR/dist/$name" ;;
app-image)
	rm -rf dist/fsh.app
	mv "$work/out/fsh.app" dist/
	echo "$PROJECT_DIR/dist/fsh.app" ;;
pkg)
	"$jpackage" --type pkg --app-image "$app" --dest "$work/pkg" --name fsh --app-version "$version" \
		--vendor "Tony Bringardner" --license-file LICENSE --mac-package-identifier us.bringardner.fsh \
		--temp "$work/temp-pkg"
	name=fsh-$version-macos-$arch.pkg
	mv "$work/pkg"/fsh-*.pkg "dist/$name"
	(cd dist && shasum -a 256 "$name" > "$name.sha256")
	echo "$PROJECT_DIR/dist/$name" ;;
esac
rm -rf "$work"
