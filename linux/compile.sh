#!/bin/sh
# Build libnativekeyboard.so for us.bringardner.fsh.NativeKeyboard.
# Run from this directory after `mvn compile` (the header is generated from the Java class).

cd "$(dirname "$0")"
if [ -z "$JAVA_HOME" ]; then
	JAVA_HOME=$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")
fi
idir=$JAVA_HOME/include
name=NativeKeyboard

# regenerate the JNI header so the function names always match the Java package
# (javac's classes go in the project's target, not the system's temporary directory)
classes=../target/jni-classes
mkdir -p "$classes" || exit 1
deps=$(cd .. && mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout) || exit 1
"$JAVA_HOME/bin/javac" -h . -d "$classes" ../src/main/java/us/bringardner/fsh/$name.java \
	-cp "../target/classes:$deps" || exit 1
mv us_bringardner_fsh_$name.h $name.h

g++ -c -fPIC -I$idir/linux -I$idir $name.cpp -o $name.o || exit 1
g++ -shared -o libnativekeyboard.so $name.o -lc

# the helper that runs a job's programs in a process group of their own
# (-ldl: dlopen for -l, in libc itself only from glibc 2.34)
cc -O2 -o fshexec fshexec.c -ldl
