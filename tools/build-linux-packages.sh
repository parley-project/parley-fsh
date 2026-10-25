#!/bin/sh
# The Linux installers, from the jlink build (tools/build-jlink.sh: target/jlink/fsh):
#
#   dist/fsh_VERSION_ARCH.deb          dpkg -i, apt install ./fsh_..._amd64.deb
#   dist/fsh-VERSION-1.ARCH.rpm        rpm -i, dnf install ./fsh-...x86_64.rpm
#
# each with a .sha256. Both install the jlink build as /opt/fsh, with /usr/bin/fsh a link to its
# launcher (/opt/fsh/bin/fsh, which starts Java through fshexec -j), and add /usr/bin/fsh to
# /etc/shells (chsh -s /usr/bin/fsh), and take it out again when fsh is removed.
#
# Usage: tools/build-linux-packages.sh [deb|rpm|all] [VERSION]
#   VERSION  the pom's, without -SNAPSHOT
#
# Needs dpkg-deb (deb) and rpmbuild (rpm; on Debian and Ubuntu: apt install rpm). The packages
# are not signed. macOS: tools/build-jpackage.sh; Windows: tools/build-msi.ps1.

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
cd "$PROJECT_DIR"

if [ "$(uname -s)" != Linux ]; then
	echo "$0: Linux only (macOS: tools/build-jpackage.sh; Windows: tools/build-msi.ps1)" >&2
	exit 1
fi
types=${1:-all}
case $types in
deb|rpm) ;;
all) types="deb rpm" ;;
*) echo "usage: $0 [deb|rpm|all] [VERSION]" >&2; exit 2 ;;
esac
version=${2:-$(awk -F'[<>]' '/<artifactId>fsh<\/artifactId>/ { getline; print $3; exit }' pom.xml)}
version=${version%-SNAPSHOT}
version=${version#v}
case $(uname -m) in
x86_64|amd64) deb_arch=amd64; rpm_arch=x86_64 ;;
aarch64|arm64) deb_arch=arm64; rpm_arch=aarch64 ;;
*) deb_arch=$(uname -m); rpm_arch=$(uname -m) ;;
esac
for t in $types; do
	case $t in
	deb) tool=dpkg-deb ;;
	rpm) tool=rpmbuild ;;
	esac
	if ! command -v $tool > /dev/null 2>&1; then
		echo "$0: no $tool (for the .$t)" >&2
		exit 1
	fi
done

image=target/jlink/fsh
if [ ! -x "$image/bin/fsh" ] || [ pom.xml -nt "$image/bin/fsh" ] || [ tools/build-jlink.sh -nt "$image/bin/fsh" ] \
		|| [ -n "$(find src resources -newer "$image/bin/fsh" -type f 2>/dev/null | head -1)" ]; then
	tools/build-jlink.sh
fi

summary="fsh, the FileSource Shell"
about="A bash-like shell whose file system is any parley-files FileSource: the local disk, memory, FTP, SFTP or a database. It carries its own Java runtime."
url=https://github.com/parley-project/parley-fsh
maintainer="Tony Bringardner <tony.bringardner@gmail.com>"
work=$PROJECT_DIR/target/linux-packages
rm -rf "$work"
mkdir -p dist

# the files both install: /opt/fsh (the jlink build) and the link /usr/bin/fsh
root=$work/root
mkdir -p "$root/opt" "$root/usr/bin"
cp -Rp "$image" "$root/opt/fsh"
ln -s /opt/fsh/bin/fsh "$root/usr/bin/fsh"
chmod -R u=rwX,go=rX "$root/opt/fsh"

# (/etc/shells: added after installing, taken out after removing, not after an upgrade)
add_shell='if [ -f /etc/shells ] && ! grep -qx /usr/bin/fsh /etc/shells; then echo /usr/bin/fsh >> /etc/shells; fi'
remove_shell='if [ -f /etc/shells ]; then grep -vx /usr/bin/fsh /etc/shells > /etc/shells.fsh-tmp || true; cat /etc/shells.fsh-tmp > /etc/shells; rm -f /etc/shells.fsh-tmp; fi'

sum() {
	(cd dist && sha256sum "$1" > "$1.sha256")
	echo "$PROJECT_DIR/dist/$1"
}

for t in $types; do
	case $t in
	deb)
		deb=$work/deb
		cp -Rp "$root" "$deb"
		mkdir -p "$deb/DEBIAN"
		cat > "$deb/DEBIAN/control" <<CTL
Package: fsh
Version: $version
Architecture: $deb_arch
Maintainer: $maintainer
Section: shells
Priority: optional
Installed-Size: $(du -sk "$root" | cut -f1)
Homepage: $url
Description: $summary
 $about
CTL
		printf '#!/bin/sh\nset -e\nif [ "$1" = configure ]; then\n\t%s\nfi\n' "$add_shell" > "$deb/DEBIAN/postinst"
		printf '#!/bin/sh\nset -e\nif [ "$1" = remove ] || [ "$1" = purge ]; then\n\t%s\nfi\n' "$remove_shell" > "$deb/DEBIAN/postrm"
		chmod 755 "$deb/DEBIAN/postinst" "$deb/DEBIAN/postrm"
		name=fsh_${version}_$deb_arch.deb
		dpkg-deb --root-owner-group --build "$deb" "dist/$name" > /dev/null
		sum "$name" ;;
	rpm)
		rpm=$work/rpm
		mkdir -p "$rpm/SPECS" "$rpm/RPMS"
		cat > "$rpm/SPECS/fsh.spec" <<SPEC
Name: fsh
Version: $version
Release: 1
Summary: $summary
License: Apache-2.0
URL: $url
Packager: $maintainer
AutoReqProv: no

%description
$about

%install
mkdir -p %{buildroot}
cp -Rp $root/. %{buildroot}/

%post
$add_shell

%postun
if [ "\$1" = 0 ]; then
	$remove_shell
fi

%files
%defattr(-,root,root,-)
/opt/fsh
/usr/bin/fsh
SPEC
		# (no debug packages, no stripping or byte-compiling the runtime's files)
		rpmbuild --quiet -bb --target "$rpm_arch" \
			--define "_topdir $rpm" --define "_build_id_links none" \
			--define "debug_package %{nil}" --define "__os_install_post %{nil}" \
			"$rpm/SPECS/fsh.spec"
		name=fsh-$version-1.$rpm_arch.rpm
		mv "$rpm/RPMS/$rpm_arch/$name" "dist/$name"
		sum "$name" ;;
	esac
done
rm -rf "$work"
