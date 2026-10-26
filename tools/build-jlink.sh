#!/bin/sh
# Build fsh with a Java runtime of its own (jlink): target/jlink/fsh, a folder that runs on this
# platform with no Java installed, started the way macos/fsh starts it. The installers are made
# from it: tools/build-jpackage.sh (macOS .dmg), tools/build-linux-packages.sh (.deb, .rpm).
#
#   target/jlink/fsh/bin/fsh                    the launcher (link to it from a directory on PATH)
#   target/jlink/fsh/lib/fsh.jar                fsh and everything it needs (mvn package)
#   target/jlink/fsh/lib/libnativekeyboard.*    the keyboard library and fshexec (compile.sh)
#   target/jlink/fsh/lib/fsh.aot or fsh.jsa     the class cache made from tools/aot-training.sh
#   target/jlink/fsh/lib/plugins                the plugin jars built in, on fsh's class path
#   target/jlink/fsh/runtime                    the Java runtime: only the modules fsh needs
#
# Usage: tools/build-jlink.sh [--debug] [--package VERSION] [--ide DIR] [--db DIR] [--viewer DIR]
#   --debug              also put the debugger agent in the runtime (FSH_DEBUG=8001 bin/fsh)
#   --package VERSION    also make dist/fsh-VERSION-PLATFORM-ARCH-jvm.tar.gz and its .sha256
#   --ide DIR            a checkout of fsh-ide: bin/fsh-ide, the Swing IDE (a window program)
#   --db DIR             a checkout of fshdb: bin/fshdb, the console debugger
#   --viewer DIR         a checkout of parley-files-viewer: bin/parley-files-viewer, the Swing
#                        file browser (a window program)
#
# The other programs (--ide, --db, --viewer) run on the same Java runtime, each with its own
# command in bin, as the Windows installer has them (tools/build-msi.ps1). Each is built in its
# checkout (it builds against fsh: tools/build-installers-with-plugins.sh installs what it needs
# first); its jar and the dependencies fsh.jar doesn't have go in lib/apps (not the JavaFX ones:
# the programs here are Swing or console programs).
#
# The plugins built in (the file browser for browse, view, edit and search) are the jars
# FSH_PLUGINS names (separated by spaces), else parley-files-viewer-tui and its Lanterna plugin
# from the Maven repository (fsh's version: mvn install in parley-files-viewer-tui), else those in
# ~/.parley/fsh/plugins and /usr/local/lib/parley/fsh/plugins; with none, fsh is built without. fsh finds each plugin
# class once, so the same plugin in a plugin folder too is not there twice.
#
# JAVA_HOME names the JDK whose runtime goes in (macOS: else /usr/libexec/java_home). The runtime
# is that JDK's version: 17 or later for the keychain, 25 or later for the AOT cache (the fastest
# start).

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_DIR"

debug=""
version=""
ide_repo=""
db_repo=""
viewer_repo=""
usage="usage: $0 [--debug] [--package VERSION] [--ide DIR] [--db DIR] [--viewer DIR]"
while [ $# -gt 0 ]; do
	case $1 in
	--debug) debug=yes ;;
	--package) version=${2#v}; [ -n "$version" ] || { echo "$0: --package needs a version" >&2; exit 2; }; shift ;;
	--ide|--db|--viewer)
		[ -n "$2" ] || { echo "$0: $1 needs a folder" >&2; exit 2; }
		case $1 in
		--ide) ide_repo=$2 ;;
		--db) db_repo=$2 ;;
		--viewer) viewer_repo=$2 ;;
		esac
		shift ;;
	*) echo "$usage" >&2; exit 2 ;;
	esac
	shift
done

# the other programs: each one's checkout, main class, and whether it is a console program
apps=""
[ -n "$ide_repo" ] && apps="$apps fsh-ide"
[ -n "$db_repo" ] && apps="$apps fshdb"
[ -n "$viewer_repo" ] && apps="$apps parley-files-viewer"
app_repo() {
	case $1 in
	fsh-ide) echo "$ide_repo" ;;
	fshdb) echo "$db_repo" ;;
	parley-files-viewer) echo "$viewer_repo" ;;
	esac
}
app_class() {
	case $1 in
	fsh-ide) echo us.bringardner.fsh.ide.FshIDE ;;
	fshdb) echo us.bringardner.fshdb.Fshdb ;;
	parley-files-viewer) echo us.bringardner.parley.files.viewer.FileSourceViewer ;;
	esac
}
app_console() {
	[ "$1" = fshdb ]
}
for a in $apps; do
	if [ ! -f "$(app_repo $a)/pom.xml" ]; then
		echo "$0: no $a checkout at $(app_repo $a)" >&2
		exit 1
	fi
done

case $(uname -s) in
Darwin) platform=macos; lib=libnativekeyboard.dylib ;;
Linux) platform=linux; lib=libnativekeyboard.so ;;
*) echo "$0: macOS and Linux only (Windows: not yet)" >&2; exit 1 ;;
esac
case $(uname -m) in
arm64|aarch64) arch=aarch64 ;;
x86_64|amd64) arch=x86_64 ;;
*) arch=$(uname -m) ;;
esac

if [ -z "$JAVA_HOME" ] && [ -x /usr/libexec/java_home ]; then
	JAVA_HOME=$(/usr/libexec/java_home)
fi
if [ -z "$JAVA_HOME" ] && command -v javac > /dev/null 2>&1; then
	JAVA_HOME=$(cd "$(dirname "$(readlink -f "$(command -v javac)")")/.." && pwd)
fi
for tool in jlink jdeps java; do
	if [ ! -x "$JAVA_HOME/bin/$tool" ]; then
		echo "$0: no $tool in JAVA_HOME ($JAVA_HOME): set JAVA_HOME to a JDK" >&2
		exit 1
	fi
done
export JAVA_HOME
major=$(sed -n 's/^JAVA_VERSION="\([0-9]*\).*/\1/p' "$JAVA_HOME/release")
echo "JDK $major ($JAVA_HOME), $platform $arch"
if [ "$major" -lt 17 ]; then
	echo "$0: note: JDK $major: the keychain needs 17 or later, so @keychain: will not be available" >&2
fi

# fsh.jar (with the keychain: its profile is on unless -Dparley.fsh.noKeychain)
mvn -q package -DskipTests
[ -f target/fsh.jar ] || { echo "$0: mvn package made no target/fsh.jar" >&2; exit 1; }

# the other programs' jars, in target/apps: each one's own (its name in NAME.main), and its
# dependencies that fsh.jar doesn't have inside
apps_dir=$PROJECT_DIR/target/apps
rm -rf "$apps_dir"
if [ -n "$apps" ]; then
	mkdir -p "$apps_dir"
	mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
	fsh_jars=$(tr ':' '\n' < target/runtime-classpath.txt | sed 's|.*/||')
	# (not fsh itself, JavaFX and what only it needs)
	skip='^(fsh-[0-9].*|javafx-.*|richtextfx-.*|flowless-.*|reactfx-.*|undofx-.*|wellbehavedfx-.*|bringardner-fx-widgets-.*|parley-files-fx-.*)\.jar$'
	for a in $apps; do
		repo=$(cd "$(app_repo $a)" && pwd)
		if ! (cd "$repo" && mvn -q package -DskipTests dependency:build-classpath -Dmdep.includeScope=runtime \
				-Dmdep.outputFile=target/app-classpath.txt); then
			echo "$0: building $a failed" >&2
			exit 1
		fi
		# (the jar this build made: its version from Maven's record of it, as target can hold older
		# ones, fsh-ide-1.0.0.jar beside fsh-ide-2.0.0-SNAPSHOT.jar; else the newest)
		v=$(sed -n 's/^version=//p' "$repo/target/maven-archiver/pom.properties" 2>/dev/null | tr -d '\r')
		main=$repo/target/$a-$v.jar
		if [ -z "$v" ] || [ ! -f "$main" ]; then
			main=$(ls -t "$repo"/target/$a-*.jar 2>/dev/null | grep -Ev -- '-(sources|javadoc|tests|all|plugin)\.jar$' | head -1 || true)
		fi
		[ -n "$main" ] || { echo "$0: no $a jar built in $repo" >&2; exit 1; }
		cp -p "$main" "$apps_dir/"
		basename "$main" > "$apps_dir/$a.main"
		n=0
		for j in $(tr ':' '\n' < "$repo/target/app-classpath.txt"); do
			name=$(basename "$j")
			if echo "$fsh_jars" | grep -qxF "$name" || echo "$name" | grep -Eq "$skip"; then
				continue
			fi
			cp -p "$j" "$apps_dir/"
			n=$((n + 1))
		done
		echo "$a: $main, and $n more jars"
	done
fi

# the keyboard library and fshexec, built from macos/ or linux/ when they are not there
if [ ! -f "$platform/$lib" ] || [ ! -x "$platform/fshexec" ] \
		|| [ "src/main/java/us/bringardner/fsh/NativeKeyboard.java" -nt "$platform/$lib" ]; then
	sh "$platform/compile.sh"
fi

# The modules: what fsh.jar uses (jdeps), and what is found only at run time:
#  jdk.crypto.ec       elliptic curves for SSH keys and key exchange (in java.base from JDK 22)
#  jdk.charsets        the encodings beyond the standard ones (iconv -f, LANG=ja_JP.eucJP ...)
#  jdk.localedata      every locale, as the JDK has them (LC_TIME, LC_NUMERIC)
#  jdk.zipfs           zip and jar files as file systems
#  jdk.naming.dns      DNS through JNDI (an SSH or FTP host given by an SRV record)
#  jdk.jdwp.agent      (--debug) the debugger
#  (and what the other programs use, with --ide, --db or --viewer: the desktop for the windows)
set -- target/fsh.jar
[ -n "$apps" ] && set -- "$@" "$apps_dir"/*.jar
modules=$("$JAVA_HOME/bin/jdeps" -q --multi-release "$major" --ignore-missing-deps --print-module-deps "$@")
extra="jdk.crypto.ec jdk.charsets jdk.localedata jdk.zipfs jdk.naming.dns"
[ -n "$debug" ] && extra="$extra jdk.jdwp.agent"
available=$("$JAVA_HOME/bin/java" --list-modules | sed 's/@.*//')
for m in $extra; do
	if echo "$available" | grep -qx "$m"; then
		modules="$modules,$m"
	fi
done
echo "modules: $modules"

out=$PROJECT_DIR/target/jlink/fsh
rm -rf "$out"
mkdir -p "$out/bin" "$out/lib"
compress=zip-6
[ "$major" -lt 21 ] && compress=2
cds=""
[ "$major" -ge 17 ] && cds=--generate-cds-archive
"$JAVA_HOME/bin/jlink" --add-modules "$modules" --output "$out/runtime" \
	--strip-debug --no-header-files --no-man-pages --compress=$compress $cds
# (the CDS archive for heaps over 32 GB, which a shell never has)
rm -f "$out/runtime/lib/server/classes_nocoops.jsa"

cp -p target/fsh.jar "$out/lib/"
# The plugins built in: FSH_PLUGINS, else the file browser from the Maven repository (fsh's
# version of parley-files-viewer-tui and its Lanterna plugin: mvn install there first), else the
# jars in the plugin folders. (Fetched, not dependencies: they depend on fsh.)
plugins=$FSH_PLUGINS
if [ -z "$plugins" ]; then
	v=$(awk -F'[<>]' '/<artifactId>fsh<\/artifactId>/ { getline; print $3; exit }' pom.xml)
	rm -rf target/plugins
	if mvn -q dependency:copy -Dmdep.useBaseVersion=true -DoutputDirectory=target/plugins \
			-Dartifact=us.bringardner.parley:parley-files-viewer-tui:$v > /dev/null 2>&1 \
			&& mvn -q dependency:copy -Dmdep.useBaseVersion=true -DoutputDirectory=target/plugins \
			-Dartifact=us.bringardner.parley:parley-files-viewer-tui-lanterna:$v:jar:plugin > /dev/null 2>&1; then
		plugins=$(ls target/plugins/*.jar)
	else
		echo "the file browser $v is not in the Maven repository (mvn install in parley-files-viewer-tui): the plugin folders' jars instead"
	fi
fi
# (|| true: ls fails when a folder isn't there, which would end the script, set -e)
[ -n "$plugins" ] || plugins=$(ls "$HOME"/.parley/fsh/plugins/*.jar /usr/local/lib/parley/fsh/plugins/*.jar 2>/dev/null || true)
if [ -n "$plugins" ]; then
	mkdir -p "$out/lib/plugins"
	for j in $plugins; do
		cp -p "$j" "$out/lib/plugins/"
	done
	echo "plugins built in: $plugins"
else
	echo "no plugins built in (FSH_PLUGINS, the Maven repository, or the plugin folders)"
fi
cp -p "$platform/$lib" "$platform/fshexec" "$out/lib/"
cp -p LICENSE "$out/"

# The launcher: macos/fsh's options, the runtime and the cache from this folder. (Built from
# set -- rather than a string, so a folder with spaces in its name works.)
cat > "$out/bin/fsh" <<'SH'
#!/bin/sh
# fsh with its own Java runtime (tools/build-jlink.sh). Keep the folder together; it may be moved,
# and this file linked from a directory on PATH. The same file is the command in the installers:
# /opt/fsh/bin/fsh (.deb, .rpm), and fsh.app/Contents/app/fsh (macOS), next to fsh.jar.
# FSH_DEBUG=PORT listens for a debugger on localhost:PORT (a runtime built with --debug).
self=$0
while [ -L "$self" ]; do
	link=$(readlink "$self")
	case $link in
	/*) self=$link ;;
	*) self=$(dirname "$self")/$link ;;
	esac
done
here=$(cd "$(dirname "$self")" && pwd)
# (the jar and libraries: beside this in an app, else in ../lib; the runtime beside those)
if [ -f "$here/fsh.jar" ]; then
	lib=$here
else
	lib=$(cd "$here/../lib" && pwd)
fi
java=$lib/../runtime/bin/java
[ -x "$java" ] || java=$lib/../runtime/Contents/Home/bin/java

# (the plugins built in: on the class path after fsh)
cp=$lib/fsh.jar
[ -d "$lib/plugins" ] && cp="$cp:$lib/plugins/*"
set -- -cp "$cp" us.bringardner.fsh.Console "$@"
if [ -n "$FSH_DEBUG" ]; then
	set -- "-agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=localhost:$FSH_DEBUG" "$@"
fi
# (the class cache from tools/aot-training.sh, quietly not used if it does not match)
if [ -f "$lib/fsh.aot" ]; then
	set -- "-XX:AOTCache=$lib/fsh.aot" -Xlog:aot=off -Xlog:cds=off "$@"
elif [ -f "$lib/fsh.jsa" ]; then
	set -- "-XX:SharedArchiveFile=$lib/fsh.jsa" -Xshare:auto -Xlog:cds=off -Xlog:cds+dynamic=off "$@"
fi
# (as macos/fsh: deep recursion; the serial collector with a small young generation; the
# open-files limit as the caller left it; never a window)
set -- @NATIVE_ACCESS@ "-Djava.library.path=$lib" -Xss16m \
	-XX:+UseSerialGC -Xmn16m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 \
	-XX:-MaxFDLimit -Djava.awt.headless=true "$@"
# (through fshexec -j: the signals the shell was started ignoring, which the JVM may take over)
if [ -x "$lib/fshexec" ]; then
	exec "$lib/fshexec" -j "$java" "$@"
fi
exec "$java" "$@"
SH
native_access=""
[ "$major" -ge 17 ] && native_access=--enable-native-access=ALL-UNNAMED
sed "s|@NATIVE_ACCESS@|$native_access|" "$out/bin/fsh" > "$out/bin/fsh.tmp"
mv "$out/bin/fsh.tmp" "$out/bin/fsh"
chmod 755 "$out/bin/fsh"

# The other programs: their jars in lib/apps, and a command for each in bin, found the same way as
# bin/fsh (beside fsh.jar in an app, else in ../lib). Each one's class path: its jar, fsh.jar,
# lib/apps and the plugins built in.
if [ -n "$apps" ]; then
	mkdir -p "$out/lib/apps"
	cp -p "$apps_dir"/*.jar "$out/lib/apps/"
fi
for a in $apps; do
	main_jar=$(cat "$apps_dir/$a.main")
	headless=""
	app_console $a && headless=-Djava.awt.headless=true
	cat > "$out/bin/$a" <<'SH'
#!/bin/sh
# @NAME@ on fsh's Java runtime (tools/build-jlink.sh --@OPTION@). Keep the folder together; this
# file may be linked from a directory on PATH. The installers' command: /opt/fsh/bin/@NAME@ (.deb,
# .rpm) and fsh.app/Contents/app/@NAME@ (macOS), next to fsh.jar.
self=$0
while [ -L "$self" ]; do
	link=$(readlink "$self")
	case $link in
	/*) self=$link ;;
	*) self=$(dirname "$self")/$link ;;
	esac
done
here=$(cd "$(dirname "$self")" && pwd)
if [ -f "$here/fsh.jar" ]; then
	lib=$here
else
	lib=$(cd "$here/../lib" && pwd)
fi
java=$lib/../runtime/bin/java
[ -x "$java" ] || java=$lib/../runtime/Contents/Home/bin/java

cp=$lib/apps/@MAIN_JAR@:$lib/fsh.jar:$lib/apps/*
[ -d "$lib/plugins" ] && cp="$cp:$lib/plugins/*"
set -- -cp "$cp" @CLASS@ "$@"
# (macOS: the Dock shows the program's name, and fsh.app's icon when it is there)
if [ "$(uname -s)" = Darwin ]; then
	set -- -Xdock:name=@NAME@ "$@"
	[ -f "$lib/../Resources/fsh.icns" ] && set -- "-Xdock:icon=$lib/../Resources/fsh.icns" "$@"
fi
# (bin/fsh's options; a window program is not headless)
set -- @NATIVE_ACCESS@ "-Djava.library.path=$lib" -Xss16m \
	-XX:+UseSerialGC -Xmn16m -XX:MinHeapFreeRatio=10 -XX:MaxHeapFreeRatio=20 \
	-XX:-MaxFDLimit @HEADLESS@ "$@"
SH
	if app_console $a; then
		# (a console program starts programs too: through fshexec -j, as bin/fsh)
		cat >> "$out/bin/$a" <<'SH'
if [ -x "$lib/fshexec" ]; then
	exec "$lib/fshexec" -j "$java" "$@"
fi
SH
	fi
	echo 'exec "$java" "$@"' >> "$out/bin/$a"
	option=ide
	[ $a = fshdb ] && option=db
	[ $a = parley-files-viewer ] && option=viewer
	sed -e "s|@NAME@|$a|g" -e "s|@OPTION@|$option|" -e "s|@MAIN_JAR@|$main_jar|" -e "s|@CLASS@|$(app_class $a)|" \
		-e "s|@NATIVE_ACCESS@|$native_access|" -e "s|@HEADLESS@|$headless|" "$out/bin/$a" > "$out/bin/$a.tmp"
	mv "$out/bin/$a.tmp" "$out/bin/$a"
	chmod 755 "$out/bin/$a"
done

# The class cache, from a run of tools/aot-training.sh: an AOT cache on JDK 25 or later (classes
# loaded and linked, and the JIT's profiles), else a CDS archive of fsh's classes (JDK 13 or later)
java="$out/runtime/bin/java"
cp=$out/lib/fsh.jar
[ -d "$out/lib/plugins" ] && cp="$cp:$out/lib/plugins/*"
opts="$native_access -Djava.library.path=$out/lib -Xss16m -XX:+UseSerialGC -Xmn16m -XX:-MaxFDLimit -Djava.awt.headless=true"
if [ "$major" -ge 25 ]; then
	"$java" -XX:AOTCacheOutput="$out/lib/fsh.aot" $opts -cp "$cp" us.bringardner.fsh.Console \
		tools/aot-training.sh > /dev/null 2>&1 || true
	rm -f "$out/lib/fsh.aot.config"
elif [ "$major" -ge 13 ]; then
	"$java" -XX:ArchiveClassesAtExit="$out/lib/fsh.jsa" $opts -cp "$cp" us.bringardner.fsh.Console \
		tools/aot-training.sh > /dev/null 2>&1 || true
fi

# does it run?
if [ "$("$out/bin/fsh" -c 'echo $((6 * 7))')" != 42 ]; then
	echo "$0: $out/bin/fsh -c 'echo \$((6 * 7))' did not print 42" >&2
	exit 1
fi
# and the other programs: java --dry-run loads each one's main class (and what it needs to start)
# from its class path, without running it (no window opens)
for a in $apps; do
	cp=$out/lib/apps/$(cat "$apps_dir/$a.main"):$out/lib/fsh.jar:$out/lib/apps/*
	[ -d "$out/lib/plugins" ] && cp="$cp:$out/lib/plugins/*"
	if ! "$out/runtime/bin/java" --dry-run -cp "$cp" "$(app_class $a)" > /dev/null; then
		echo "$0: $a's main class $(app_class $a) does not load on the runtime" >&2
		exit 1
	fi
	echo "$out/bin/$a"
done
echo "$out ($(du -sh "$out" | cut -f1)): run $out/bin/fsh"

if [ -n "$version" ]; then
	name=fsh-$version-$platform-$arch-jvm
	dist=$PROJECT_DIR/dist
	mkdir -p "$dist"
	rm -rf "${dist:?}/$name"
	cp -Rp "$out" "$dist/$name"
	cat > "$dist/$name/README.txt" <<TXT
fsh $version for $platform ($arch), with its own Java runtime

Run bin/fsh. Everything it needs is in this folder; keep it together. Put bin on PATH,
or link bin/fsh from a directory that is.

TXT
	if [ "$platform" = macos ]; then
		cat >> "$dist/$name/README.txt" <<'TXT'
macOS marks a download as quarantined, and these files are not notarized. Once, after
unpacking:

    xattr -dr com.apple.quarantine .

TXT
	fi
	echo "Source and documentation: https://github.com/parley-project/parley-fsh" >> "$dist/$name/README.txt"
	(cd "$dist" && tar -czf "$name.tar.gz" "$name")
	rm -rf "${dist:?}/$name"
	(cd "$dist" && if command -v sha256sum > /dev/null 2>&1; then sha256sum "$name.tar.gz"; else shasum -a 256 "$name.tar.gz"; fi > "$name.tar.gz.sha256")
	echo "$dist/$name.tar.gz"
fi
