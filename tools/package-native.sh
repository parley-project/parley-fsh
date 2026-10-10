#!/bin/sh
# Package the native build (target/native: macos|linux/build-native.sh) as the download for this
# platform: dist/fsh-VERSION-PLATFORM-ARCH.tar.gz and its .sha256.
#
# Usage: tools/package-native.sh PLATFORM ARCH VERSION     (PLATFORM: macos or linux)
#   e.g. tools/package-native.sh macos aarch64 v1.0.0  ->  dist/fsh-1.0.0-macos-aarch64.tar.gz
#
# The archive is one folder with everything next to the launcher, so it runs from wherever it is
# unpacked: fsh (the launcher), fsh-image (the native image), the keyboard library, fshexec.
# Windows is packaged by tools/package-native.ps1.

set -e
if [ $# -ne 3 ]; then
	echo "usage: $0 macos|linux ARCH VERSION" >&2
	exit 2
fi
platform=$1
arch=$2
version=${3#v}

project=$(cd "$(dirname "$0")/.." && pwd)
native=$project/target/native
case $platform in
macos) lib=libnativekeyboard.dylib ;;
linux) lib=libnativekeyboard.so ;;
*) echo "$0: platform is macos or linux, not $platform" >&2; exit 2 ;;
esac
for f in fsh fsh-image $lib fshexec; do
	if [ ! -f "$native/$f" ]; then
		echo "$0: $native/$f is not there: run $platform/build-native.sh first" >&2
		exit 1
	fi
done

name=fsh-$version-$platform-$arch
dist=$project/dist
stage=$dist/$name
rm -rf "$stage"
mkdir -p "$stage"
for f in fsh fsh-image $lib fshexec; do
	cp -p "$native/$f" "$stage/"
done
cp -p "$project/LICENSE" "$stage/"

cat > "$stage/README.txt" <<TXT
fsh $version for $platform ($arch)

Run ./fsh. Everything it needs is in this folder; keep the files together
(fsh starts fsh-image, which loads $lib and uses fshexec).
Put the folder on PATH, or link ./fsh from a directory that is.

TXT
if [ "$platform" = macos ]; then
	cat >> "$stage/README.txt" <<'TXT'
macOS marks a download as quarantined, and these files are not notarized. Once, after
unpacking:

    xattr -dr com.apple.quarantine .

TXT
fi
echo "Source and documentation: https://github.com/tony-bringardner/fsh" >> "$stage/README.txt"

(cd "$dist" && tar -czf "$name.tar.gz" "$name")
rm -rf "$stage"
(cd "$dist" && if command -v sha256sum >/dev/null 2>&1; then sha256sum "$name.tar.gz"; else shasum -a 256 "$name.tar.gz"; fi > "$name.tar.gz.sha256")
echo "$dist/$name.tar.gz"
