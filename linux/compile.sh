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

# The compilers. What this machine's own build runs on this machine's glibc or later (and, built on
# glibc 2.34 or later, never on an older one). FSH_GLIBC=2.17 builds for that glibc and every later
# one instead, whatever this machine has: what is published (the native jars, the installers) is
# built that way, to run on older systems too (Amazon Linux 2, RHEL 7 ...). That takes zig's C and
# C++ compilers, which carry every glibc's headers: pip install ziglang.
if [ -n "$FSH_GLIBC" ]; then
	case $(uname -m) in
	x86_64 | amd64) zarch=x86_64 ;;
	aarch64 | arm64) zarch=aarch64 ;;
	*) echo "compile.sh: FSH_GLIBC: no target for $(uname -m)" >&2; exit 1 ;;
	esac
	if command -v zig > /dev/null 2>&1; then
		zig=zig
	elif python3 -c 'import ziglang' 2> /dev/null; then
		zig="python3 -m ziglang"
	else
		echo "compile.sh: FSH_GLIBC=$FSH_GLIBC needs zig: pip install ziglang" >&2
		exit 1
	fi
	# (-s: without the debugging information zig leaves in)
	CXX="$zig c++ -target $zarch-linux-gnu.$FSH_GLIBC -s"
	CC="$zig cc -target $zarch-linux-gnu.$FSH_GLIBC -s"
else
	CXX=g++
	CC=cc
fi

$CXX -c -fPIC -I$idir/linux -I$idir $name.cpp -o $name.o || exit 1
$CXX -shared -o libnativekeyboard.so $name.o -lc || exit 1

# the helper that runs a job's programs in a process group of their own
# (-ldl: dlopen for -l, in libc itself only from glibc 2.34; glibc-compat.h: built on glibc 2.38 or
# later it still runs on 2.34)
$CC -O2 -include glibc-compat.h -o fshexec fshexec.c -ldl || exit 1
