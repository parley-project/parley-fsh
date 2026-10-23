#!/bin/sh
# Build fsh as a native image (GraalVM 21 or later): target/native/fsh, with the library and
# fshexec next to it. It starts in a few milliseconds where the JVM takes about 0.15 s; linux/fsh
# runs it when it is newer than the jar. Its settings are in
# src/main/resources/META-INF/native-image/us.bringardner/fsh.
#
# GRAALVM_HOME (or JAVA_HOME) names the GraalVM; else the newest one installed is used.
#
# --pgo (Oracle GraalVM): profile-guided optimization, which a native image needs to run scripts
# about as fast as the JVM's JIT does: an instrumented image runs tools/aot-training.sh, and the
# image is built again from what it recorded (three times as long to build).
#
# --crema (GraalVM 25.1 or later, experimental): an image that loads classes at run time
# (-H:+RuntimeClassLoading), so it loads plugin jars as the JVM build does: target/native-crema.
# A plugin's bytecode is interpreted, and it can call only methods compiled into the image: the
# methods the plugin jars found at build time use are listed for the image (CREMA_PLUGINS names the
# jars; else those in the plugin folders), and java.lang and java.lang.invoke are kept whole.
# --crema --jit: the same, with Ristretto compiling the plugins' hot methods at run time
# (-H:+GraalJITCompileAtRuntime): target/native-crema-jit.

LIB_DIR=$(cd "$(dirname "$0")" && pwd)
PROJECT_DIR=$(dirname "$LIB_DIR")
cd "$PROJECT_DIR" || exit 1

graal=${GRAALVM_HOME:-}
if [ -z "$graal" ] && [ -n "$JAVA_HOME" ] && [ -x "$JAVA_HOME/bin/native-image" ]; then
	graal=$JAVA_HOME
fi
if [ -z "$graal" ]; then
	graal=$(ls -d /usr/lib/jvm/graalvm* /opt/graalvm* "$HOME"/.sdkman/candidates/java/*graal* 2>/dev/null | tail -1)
fi
if [ -z "$graal" ] || [ ! -x "$graal/bin/native-image" ]; then
	echo "build-native.sh: no GraalVM native-image (set GRAALVM_HOME)" >&2
	exit 1
fi

mvn -q package -DskipTests || exit 1
jar=$(ls -t target/fsh-*.jar | head -1)
mvn -q dependency:build-classpath -Dparley.fsh.noKeychain -Dmdep.includeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt || exit 1
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
		-Dparley.fsh.libdir="$PROJECT_DIR/$out" "$@" -o "$out/fsh-image" us.bringardner.fsh.Console || exit 1
	cp -f "$LIB_DIR/libnativekeyboard.so" "$LIB_DIR/fshexec" "$out/"
	# (fsh: the image's launcher, which notes the open-files limit the image raises)
	cc -O2 -DIMAGE_DIR="\"$PROJECT_DIR/$out\"" -o "$out/fsh" "$LIB_DIR/fsh-native.c" || exit 1
}
case "$1" in
--pgo)
	build target/native-instrumented --pgo-instrument
	# (the profile, default.iprof, is written where it runs)
	(cd target/native-instrumented && rm -f default.iprof && TMPDIR="$PROJECT_DIR/target/native-tmp" ./fsh-image "$PROJECT_DIR/tools/aot-training.sh" > /dev/null) || exit 1
	build target/native --pgo="$PROJECT_DIR/target/native-instrumented/default.iprof" ;;
--crema)
	# (interpreted plugin code can call only methods compiled into the image: those the plugin
	# jars use are listed for it (tools/CremaPluginConfig.java), and java.lang and
	# java.lang.invoke are kept whole, for lambdas and string concatenation, whose generated
	# classes run interpreted too; CREMA_PRESERVE adds more, e.g. package=java.util)
	plugins=${CREMA_PLUGINS:-$(ls "$HOME"/.parley/fsh/plugins/*.jar /usr/local/lib/parley/fsh/plugins/*.jar 2>/dev/null)}
	mkdir -p target/native-crema-config
	if [ -n "$plugins" ]; then
		"$graal/bin/java" "$PROJECT_DIR/tools/CremaPluginConfig.java" target/native-crema-config/reflect-config.json \
			"$jar:$(cat target/runtime-classpath.txt)" $plugins || exit 1
	fi
	# (the keyboard library binds its methods with RegisterNatives as it loads, since a Crema image
	# does not find them by name: it needs the class)
	echo '[{"name": "us.bringardner.fsh.NativeKeyboard"}]' > target/native-crema-config/jni-config.json
	keep="package=java.lang,package=java.lang.invoke${CREMA_PRESERVE:+,$CREMA_PRESERVE}"
	out=target/native-crema
	jit=""
	if [ "$2" = "--jit" ]; then
		out=target/native-crema-jit
		jit="-H:+GraalJITCompileAtRuntime"
	fi
	build $out -H:+UnlockExperimentalVMOptions -H:+RuntimeClassLoading $jit -H:Preserve="$keep" --enable-native-access=ALL-UNNAMED \
		-H:ConfigurationFileDirectories=target/native-crema-config ;;
"")
	build target/native ;;
*)
	echo "build-native.sh: $1: use --pgo, --crema or --crema --jit" >&2
	exit 2 ;;
esac
