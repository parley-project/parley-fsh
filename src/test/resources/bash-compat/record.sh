#!/bin/sh
# Record what bash does for each case: cases/NAME.sh -> cases/NAME.out (stdout) and cases/NAME.status.
# TestBashCompat runs the same cases through BjlShell and compares. Run it after adding or changing a
# case:  sh src/test/resources/bash-compat/record.sh [bash]
#
# Each case runs in a new empty directory, with no profile and a small fixed environment, so the
# results do not depend on the machine. The bash version is written to recorded-with.txt.

cd "$(dirname "$0")" || exit 1
# the full path: env -i below has a small PATH, where bash may be an older one (macOS /bin/bash is 3.2)
BASH_BIN=$(command -v "${1:-bash}") || { echo "no bash: ${1:-bash}"; exit 1; }
HERE=$(pwd)

echo "$BASH_BIN: $("$BASH_BIN" --version | head -1)" > recorded-with.txt

for f in cases/*.sh; do
	name=${f%.sh}
	dir=$(mktemp -d)
	(
		cd "$dir" || exit 1
		env -i PATH=/usr/bin:/bin HOME=/home/test LANG=C "$BASH_BIN" --noprofile --norc "$HERE/$f" < /dev/null > "$HERE/$name.out" 2> /dev/null
		echo $? > "$HERE/$name.status"
	)
	rm -rf "$dir"
done

echo "recorded $(ls cases/*.sh | wc -l | tr -d ' ') cases with $(cat recorded-with.txt)"
