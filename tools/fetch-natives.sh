#!/bin/sh
# Fetch every platform's native files (the keyboard library and fshexec) that parley-build's
# preview workflow built, into natives/PLATFORM/, so that the next deploy from this machine has
# every platform's native jar (fsh-VERSION-linux-x86_64.jar ...), not only this machine's:
#
#   tools/fetch-natives.sh            from the newest preview run that succeeded
#   tools/fetch-natives.sh RUN-ID     from that run (Actions > preview, the number in its address)
#   mvn -Prelease clean deploy        (or -Psnapshots): the pom makes a native jar of each folder
#
# Needs GitHub's gh, signed in (gh auth login). It stops when a platform is missing, or when the
# native sources here differ from the commit the files were built from. Remove natives/ afterwards
# to go back to this machine's own native files.
set -e
cd "$(dirname "$0")/.."
repo=${PARLEY_BUILD_REPO:-parley-project/parley-build}
platforms="linux-x86_64 linux-aarch64 macos-x86_64 macos-aarch64 windows-x86_64"

command -v gh > /dev/null 2>&1 || { echo "fetch-natives.sh: gh is needed (https://cli.github.com), signed in" >&2; exit 1; }
run=$1
if [ -z "$run" ]; then
	run=$(gh run list -R "$repo" -w preview -s success -L 1 --json databaseId -q '.[0].databaseId')
	[ -n "$run" ] || { echo "fetch-natives.sh: $repo has no preview run that succeeded" >&2; exit 1; }
fi
echo "native files from $repo run $run"

tmp=target/fetch-natives
rm -rf "$tmp"
mkdir -p "$tmp"
gh run download "$run" -R "$repo" -p 'fsh-natives-*' -D "$tmp"

rm -rf natives
for p in $platforms; do
	[ -d "$tmp/fsh-natives-$p" ] || { echo "fetch-natives.sh: run $run has no native files for $p" >&2; rm -rf natives; exit 1; }
	mkdir -p "natives/$p"
	cp "$tmp/fsh-natives-$p"/* "natives/$p/"
	built=$(cat "natives/$p/COMMIT" 2> /dev/null || true)
	# (the same native sources here as there: else the files are not this version's)
	if [ -z "$built" ] || ! git cat-file -e "$built^{commit}" 2> /dev/null; then
		echo "fetch-natives.sh: $p was built from commit ${built:-(not said)}, which is not in this repository: git fetch, or name another run" >&2
		rm -rf natives
		exit 1
	fi
	if ! git diff --quiet "$built" HEAD -- linux macos windows src/main/java/us/bringardner/fsh/NativeKeyboard.java; then
		echo "fetch-natives.sh: the native sources have changed since $p was built (commit $built): run the preview workflow on this commit first" >&2
		rm -rf natives
		exit 1
	fi
	echo "  $p: $(ls "natives/$p" | grep -v '^COMMIT$' | tr '\n' ' ')(commit $(echo "$built" | cut -c1-7))"
done
rm -rf "$tmp"
echo "natives/ is ready: the next mvn deploy makes a native jar for each of: $platforms"
