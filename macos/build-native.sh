#!/bin/sh
# Build fsh as a native image (GraalVM 21 or later): target/native/fsh, with the library and
# fshexec next to it. It starts in a few milliseconds where the JVM takes about 0.15 s; macos/fsh
# runs it when it is newer than the jar. Its settings are in
# src/main/resources/META-INF/native-image/us.bringardner/fsh.
#
# GRAALVM_HOME (or JAVA_HOME) names the GraalVM; else the newest one installed is used.
#
# --pgo (Oracle GraalVM): profile-guided optimization, which a native image needs to run scripts
# about as fast as the JVM's JIT does: an instrumented image runs tools/aot-training.sh, and the
# image is built again from what it recorded (three times as long to build).

LIB_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(dirname "$LIB_DIR")
cd "$PROJECT_DIR" || exit 1

graal=${GRAALVM_HOME:-}
if [ -z "$graal" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/native-image" ]; then
	graal=$JAVA_HOME
fi
if [ -z "$graal" ]; then
	graal=$(ls -d /Library/Java/JavaVirtualMachines/graalvm*/Contents/Home 2>/dev/null | tail -1)
fi
if [ -z "$graal" ] || [ ! -x "$graal/bin/native-image" ]; then
	echo "build-native.sh: no GraalVM native-image (set GRAALVM_HOME)" >&2
	exit 1
fi

mvn -q package -DskipTests || exit 1
jar=$(ls -t target/fsh-*.jar | head -1)
mvn -q dependency:build-classpath -Dmdep.includeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt || exit 1
mkdir -p target/native target/native-tmp target/native-config
# (all of fsh's and parley's classes for reflection, besides what tools/aot-training.sh used)
jars=$(printf '%s:%s' "$jar" "$(cat target/runtime-classpath.txt)" | tr ':' '\n' | grep -E 'fsh-|parley' | tr '\n' ' ')
python3 -I "$PROJECT_DIR/tools/native-reflect.py" $jars target/native-config/reflect-config.json > /dev/null || exit 1
# (its work files in target, not the system's temporary directory)
build() { # output-directory [native-image options]
	out=$1
	shift
	mkdir -p "$out"
	TMPDIR="$PROJECT_DIR/target/native-tmp" "$graal/bin/native-image" -cp "$jar:$(cat target/runtime-classpath.txt)" \
		-H:TempDirectory="$PROJECT_DIR/target/native-tmp" -H:ConfigurationFileDirectories=target/native-config \
		-Dfsh.libdir="$PROJECT_DIR/$out" "$@" -o "$out/fsh" us.bringardner.fsh.Console || exit 1
	cp -f "$LIB_DIR/libnativekeyboard.dylib" "$LIB_DIR/fshexec" "$out/"
}
if [ "$1" = "--pgo" ]; then
	build target/native-instrumented --pgo-instrument
	# (the profile, default.iprof, is written where it runs)
	(cd target/native-instrumented && rm -f default.iprof && TMPDIR="$PROJECT_DIR/target/native-tmp" ./fsh "$PROJECT_DIR/tools/aot-training.sh" > /dev/null) || exit 1
	build target/native --pgo="$PROJECT_DIR/target/native-instrumented/default.iprof"
else
	build target/native
fi
