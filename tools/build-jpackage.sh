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
# With the other programs the jlink build has (tools/build-jlink.sh --ide, --db, --viewer:
# fsh-ide, fshdb, parley-files-viewer), the disk image holds a folder, Parley (as the Windows
# installer's Start menu folder), to drag to Applications:
#
#   Parley/fsh.app                  as above, and the other programs' commands beside fsh's:
#                                   Contents/app/fsh-ide, fshdb, parley-files-viewer (their jars
#                                   in Contents/app/apps), on fsh.app's Java runtime
#   Parley/fsh-ide.app              small apps (a script and the icon) that start those commands
#   Parley/fshdb.app                in fsh.app: fsh-ide and parley-files-viewer open their
#   Parley/parley-files-viewer.app  windows, fshdb opens Terminal as fsh.app does. They need
#                                   fsh.app in the same folder.
#
# Usage: tools/build-jpackage.sh [TYPE] [VERSION]
#   TYPE     dmg (the default), pkg (installs /Applications/fsh.app, or with the other programs
#            /Applications/Parley) or app-image (fsh.app only, or the Parley folder)
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

# the other programs the jlink build has: their commands beside fsh's (their jars are in lib/apps)
apps=""
for a in fsh-ide fshdb parley-files-viewer; do
	if [ -x "$image/bin/$a" ]; then
		apps="$apps $a"
		cp -p "$image/bin/$a" "$work/input/$a"
	fi
done
# where fsh.app is, in the disk image and once installed: with the other programs, in Parley
folder=""
apps_path=/Applications/fsh.app
if [ -n "$apps" ]; then
	folder=Parley
	apps_path=/Applications/$folder/fsh.app
	echo "with:$apps"
fi

{
	echo "fsh $version for macOS ($arch)"
	echo
	if [ -n "$folder" ]; then
		cat <<TXT
Drag the $folder folder to Applications. It holds fsh.app and, on fsh.app's Java runtime:
$(for a in $apps; do case $a in
fsh-ide) echo "  fsh-ide.app               an editor and debugger for shell scripts" ;;
fshdb) echo "  fshdb.app                 a debugger for shell scripts, in a Terminal window" ;;
parley-files-viewer) echo "  parley-files-viewer.app   a file browser for any file system" ;;
esac; done)
Keep them together in that folder: the others start their programs in fsh.app.
Opened from the Finder, fsh.app opens a Terminal window running fsh.

To use fsh in a terminal, link its command from a directory on PATH, once (the other
programs' commands are beside it, in the same folder):

    sudo mkdir -p /usr/local/bin
    sudo ln -sf $apps_path/Contents/app/fsh /usr/local/bin/fsh
$(for a in $apps; do echo "    sudo ln -sf $apps_path/Contents/app/$a /usr/local/bin/$a"; done)
TXT
	else
		cat <<TXT
Drag fsh.app to Applications. Opened from the Finder, it opens a Terminal window running fsh.

To use fsh in a terminal, link its command from a directory on PATH, once:

    sudo mkdir -p /usr/local/bin
    sudo ln -sf $apps_path/Contents/app/fsh /usr/local/bin/fsh
TXT
	fi
	cat <<TXT

and to use it as your login shell, add it to /etc/shells and choose it:

    echo /usr/local/bin/fsh | sudo tee -a /etc/shells
    chsh -s /usr/local/bin/fsh

The apps are not notarized: macOS asks before opening each the first time (right-click it,
Open), or, once, in a terminal:

    xattr -dr com.apple.quarantine /Applications/${folder:-fsh.app}

Source and documentation: https://github.com/parley-project/parley-fsh
TXT
} > "$work/dmg/README.txt"

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

# The other programs' apps: a script that starts the program's command in fsh.app (in the same
# folder), and fsh.app's icon. (fshdb, a console program, in Terminal, as fsh.app.)
# $1 the program, $2 what it is, $3 yes for a console program
small_app() {
	dir=$work/out/$1.app
	mkdir -p "$dir/Contents/MacOS" "$dir/Contents/Resources"
	cp -p macos/fsh.icns "$dir/Contents/Resources/fsh.icns"
	cat > "$dir/Contents/Info.plist" <<PLIST
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
	<key>CFBundleDevelopmentRegion</key><string>en</string>
	<key>CFBundleExecutable</key><string>$1</string>
	<key>CFBundleIconFile</key><string>fsh</string>
	<key>CFBundleIdentifier</key><string>us.bringardner.$1</string>
	<key>CFBundleInfoDictionaryVersion</key><string>6.0</string>
	<key>CFBundleName</key><string>$1</string>
	<key>CFBundleGetInfoString</key><string>$2</string>
	<key>CFBundlePackageType</key><string>APPL</string>
	<key>CFBundleShortVersionString</key><string>$version</string>
	<key>CFBundleVersion</key><string>$version</string>
	<key>NSHumanReadableCopyright</key><string>Copyright 2004-$(date +%Y) Tony Bringardner</string>
	<key>NSHighResolutionCapable</key><true/>
</dict>
</plist>
PLIST
	cat > "$dir/Contents/MacOS/$1" <<SH
#!/bin/sh
# $1.app opened from the Finder: $1, the command in fsh.app, which is in the same folder as this
# app (Parley) and carries the Java runtime
folder=\$(cd "\$(dirname "\$0")/../../.." && pwd)
cmd=\$folder/fsh.app/Contents/app/$1
if [ ! -x "\$cmd" ]; then
	osascript -e 'display alert "$1 needs fsh.app" message "Keep $1.app in the same folder as fsh.app (the Parley folder)."' > /dev/null 2>&1
	exit 1
fi
SH
	if [ "$3" = yes ]; then
		echo 'exec open -a Terminal "$cmd"' >> "$dir/Contents/MacOS/$1"
	else
		echo 'exec "$cmd" "$@"' >> "$dir/Contents/MacOS/$1"
	fi
	chmod 755 "$dir/Contents/MacOS/$1"
	codesign --force --sign - "$dir"
}
for a in $apps; do
	case $a in
	fsh-ide) small_app fsh-ide "fsh-ide, an editor and debugger for shell scripts" no ;;
	fshdb) small_app fshdb "fshdb, a debugger for shell scripts that runs in a terminal" yes ;;
	parley-files-viewer) small_app parley-files-viewer "parley-files-viewer, a file browser for any file system" no ;;
	esac
done

# what is installed: fsh.app, or the Parley folder with all the apps
if [ -n "$folder" ]; then
	mkdir -p "$work/$folder"
	for a in fsh $apps; do
		mv "$work/out/$a.app" "$work/$folder/"
	done
	top=$work/$folder
else
	top=$app
fi

case $type in
dmg)
	# (the disk image: fsh.app or the Parley folder, a link to Applications to drag it to,
	# README.txt and LICENSE)
	mv "$top" "$work/dmg/"
	ln -s /Applications "$work/dmg/Applications"
	cp -p LICENSE "$work/dmg/"
	name=fsh-$version-macos-$arch.dmg
	rm -f "dist/$name"
	hdiutil create -quiet -volname "fsh $version" -srcfolder "$work/dmg" -fs HFS+ -format UDZO "dist/$name"
	(cd dist && shasum -a 256 "$name" > "$name.sha256")
	echo "$PROJECT_DIR/dist/$name" ;;
app-image)
	rm -rf "dist/$(basename "$top")"
	mv "$top" dist/
	echo "$PROJECT_DIR/dist/$(basename "$top")" ;;
pkg)
	name=fsh-$version-macos-$arch.pkg
	if [ -n "$folder" ]; then
		# (jpackage's pkg holds one app: pkgbuild installs the folder, /Applications/Parley, with
		# its apps installed where they are, not moved to another copy macOS knows of)
		pkg_root=$work/pkg-root
		mkdir -p "$pkg_root" "$work/pkg"
		mv "$top" "$pkg_root/"
		pkgbuild --analyze --root "$pkg_root" "$work/components.plist" > /dev/null
		i=0
		while /usr/libexec/PlistBuddy -c "Print :$i" "$work/components.plist" > /dev/null 2>&1; do
			/usr/libexec/PlistBuddy -c "Set :$i:BundleIsRelocatable false" "$work/components.plist"
			i=$((i + 1))
		done
		pkgbuild --quiet --root "$pkg_root" --component-plist "$work/components.plist" \
			--install-location /Applications --identifier us.bringardner.fsh --version "$version" "$work/pkg/fsh.pkg"
		mv "$work/pkg/fsh.pkg" "dist/$name"
	else
		"$jpackage" --type pkg --app-image "$app" --dest "$work/pkg" --name fsh --app-version "$version" \
			--vendor "Tony Bringardner" --license-file LICENSE --mac-package-identifier us.bringardner.fsh \
			--temp "$work/temp-pkg"
		mv "$work/pkg"/fsh-*.pkg "dist/$name"
	fi
	(cd dist && shasum -a 256 "$name" > "$name.sha256")
	echo "$PROJECT_DIR/dist/$name" ;;
esac
rm -rf "$work"
