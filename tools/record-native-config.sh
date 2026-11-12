#!/bin/sh
# Records what fsh's native image needs of the JDK and the other libraries (reflection, JNI,
# resources), by running fsh on a JVM under GraalVM's tracing agent, into
# src/main/resources/META-INF/native-image/us.bringardner/fsh/reachability-metadata.json.
# fsh's and parley's own classes and files are not kept there: the build lists them from the jars
# (tools/native-reflect.py), so only this recording can go out of date, and only when fsh starts
# to use something new of the JDK's (a cipher, a locale's data, a service).
#
#   tools/record-native-config.sh [SCRIPT ...]
#
# Runs tools/aot-training.sh, then each SCRIPT (fsh scripts of your own: connect to an SFTP and an
# FTP server in one, so the ciphers and key exchanges they use are recorded), then, at a terminal,
# an interactive fsh: use the line editor, view, browse and search, and exit. The agent merges into
# the file, so it only ever adds; delete the file first to record from nothing. Commit the result.
#
# Needs a GraalVM JDK (GRAALVM_HOME, else JAVA_HOME, else java on the PATH), Maven, python3, and
# the keyboard library (linux/compile.sh or macos/compile.sh).

set -e
project=$(cd "$(dirname "$0")/.." && pwd)
cd "$project"

case $(uname -s) in
Darwin) lib=$project/macos ;;
*) lib=$project/linux ;;
esac
java=java
[ -n "$JAVA_HOME" ] && java=$JAVA_HOME/bin/java
[ -n "$GRAALVM_HOME" ] && java=$GRAALVM_HOME/bin/java

out=$project/src/main/resources/META-INF/native-image/us.bringardner/fsh
mkdir -p "$out"

mvn -B -q package -DskipTests
mvn -B -q dependency:build-classpath -Dparley.fsh.noKeychain -Dmdep.includeScope=runtime -Dmdep.outputFile=target/runtime-classpath.txt
# (not fsh-VERSION-PLATFORM.jar, the keyboard library's jar)
jar=$(ls -t target/fsh-*.jar | grep -v -E -- '-(linux|macos|windows)-' | head -1)
cp="$jar:$(cat target/runtime-classpath.txt)"

run() {
	"$java" -agentlib:native-image-agent=config-merge-dir="$out" --enable-native-access=ALL-UNNAMED \
		-Djava.library.path="$lib" -cp "$cp" us.bringardner.fsh.Console "$@"
}
run tools/aot-training.sh > /dev/null
for script in "$@"; do
	run "$script"
done
if [ -t 0 ]; then
	echo "An interactive fsh, recorded: edit a line, view, browse, search, then exit."
	run
fi

jars=$(printf '%s' "$cp" | tr ':' '\n' | grep -E 'fsh-|parley' | tr '\n' ' ')
python3 -I tools/native-reflect.py --strip "$out/reachability-metadata.json" $jars
echo "Recorded into $out/reachability-metadata.json"
