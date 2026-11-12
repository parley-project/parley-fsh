#!/bin/sh
# The macOS installer: dist/fsh-VERSION-macos-ARCH.pkg (and its .sha256), made with pkgbuild from
# the jlink build (tools/build-jlink.sh: target/jlink/fsh). fsh is a command, not a desktop
# application: the package puts nothing in /Applications. It installs
#
#   /usr/local/lib/parley/fsh       the jlink build: bin, lib (fsh.jar, the keyboard library,
#                                   fshexec, the class cache, the plugins built in) and runtime
#                                   (the Java runtime)
#   /usr/local/bin/fsh              a link to /usr/local/lib/parley/fsh/bin/fsh
#   /usr/local/bin/fshdb            the same for fshdb, the console debugger, when the jlink
#                                   build has it (tools/build-jlink.sh --db)
#
# and adds /usr/local/bin/fsh to /etc/shells (chsh -s /usr/local/bin/fsh), as the Linux packages
# do for /usr/bin/fsh. /usr/local/bin is on macOS's PATH (/etc/paths), so the commands are there
# in a new terminal. A newer package replaces an older one: /usr/local/lib/parley/fsh is removed
# before the new files are written.
#
# To remove fsh:
#
#   sudo rm -rf /usr/local/lib/parley/fsh /usr/local/bin/fsh /usr/local/bin/fshdb
#   sudo sed -i '' '\|^/usr/local/bin/fsh$|d' /etc/shells
#   sudo pkgutil --forget us.bringardner.fsh
#
# Usage: tools/build-macos-pkg.sh [VERSION]
#   VERSION  the pom's, without -SNAPSHOT
#
# The package is not signed with a Developer ID or notarized (for that: productsign with a
# Developer ID Installer certificate, then notarytool), so the Finder asks before opening it
# (right-click it, Open); from a terminal: sudo installer -pkg FILE -target /
# Linux: tools/build-linux-packages.sh; Windows: tools/build-msi.ps1.

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_DIR"

if [ "$(uname -s)" != Darwin ]; then
	echo "$0: macOS only (Linux: tools/build-linux-packages.sh; Windows: tools/build-msi.ps1)" >&2
	exit 1
fi
if [ $# -gt 1 ]; then
	echo "usage: $0 [VERSION]" >&2
	exit 2
fi
version=${1:-$(awk -F'[<>]' '/<artifactId>fsh<\/artifactId>/ { getline; print $3; exit }' pom.xml)}
version=${version%-SNAPSHOT}
version=${version#v}
case $(uname -m) in
arm64) arch=aarch64 ;;
*) arch=$(uname -m) ;;
esac

image=target/jlink/fsh
if [ ! -x "$image/bin/fsh" ] || [ pom.xml -nt "$image/bin/fsh" ] || [ tools/build-jlink.sh -nt "$image/bin/fsh" ] \
		|| [ -n "$(find src resources -newer "$image/bin/fsh" -type f 2>/dev/null | head -1)" ]; then
	tools/build-jlink.sh
fi

# where it is installed, and the commands linked from /usr/local/bin
home=/usr/local/lib/parley/fsh
commands=fsh
if [ -x "$image/bin/fshdb" ]; then
	commands="$commands fshdb"
	echo "with: fshdb"
fi

work=$PROJECT_DIR/target/macos-pkg
rm -rf "$work"
mkdir -p "$work/root" "$work/scripts" dist
# (the package's root is installed in /usr/local/lib/parley: its one folder, fsh)
cp -Rp "$image" "$work/root/fsh"
chmod -R u=rwX,go=rX "$work/root/fsh"

# (an older version's files, so none of its jars is left on the class path beside the new ones)
cat > "$work/scripts/preinstall" <<SH
#!/bin/sh
rm -rf $home
exit 0
SH
{
	echo '#!/bin/sh'
	echo 'mkdir -p /usr/local/bin'
	for c in $commands; do
		echo "ln -sf $home/bin/$c /usr/local/bin/$c"
	done
	# (/etc/shells: so fsh can be a login shell)
	echo 'if [ -f /etc/shells ] && ! grep -qx /usr/local/bin/fsh /etc/shells; then'
	echo '	echo /usr/local/bin/fsh >> /etc/shells'
	echo 'fi'
	echo 'exit 0'
} > "$work/scripts/postinstall"
chmod 755 "$work/scripts/preinstall" "$work/scripts/postinstall"

name=fsh-$version-macos-$arch.pkg
rm -f "dist/$name"
pkgbuild --quiet --root "$work/root" --install-location /usr/local/lib/parley --scripts "$work/scripts" \
	--identifier us.bringardner.fsh --version "$version" "dist/$name"
(cd dist && shasum -a 256 "$name" > "$name.sha256")
rm -rf "$work"
echo "$PROJECT_DIR/dist/$name"
