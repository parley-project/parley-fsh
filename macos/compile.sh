#!/bin/sh
# Build libnativekeyboard.dylib for us.bringardner.shell.NativeKeyboard.
# Run from this directory after `mvn compile` (the header is generated from the Java class).

cd "$(dirname "$0")"
JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home)}
idir=$JAVA_HOME/include
name=NativeKeyboard

# regenerate the JNI header so the function names always match the Java package
classes=$(mktemp -d)
deps=$(cd .. && mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) || exit 1
"$JAVA_HOME/bin/javac" -h . -d "$classes" ../src/main/java/us/bringardner/shell/$name.java \
	-cp "../target/classes:$deps" || exit 1
mv us_bringardner_shell_$name.h $name.h

g++ -c -fPIC -I$idir/darwin -I$idir $name.cpp -o $name.o || exit 1
g++ -dynamiclib -o libnativekeyboard.dylib $name.o -lc
