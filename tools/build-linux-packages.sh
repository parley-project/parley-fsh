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
# The other programs the jlink build has (tools/build-jlink.sh --ide, --db, --viewer: fsh-ide,
# fshdb, parley-files-viewer, on the same runtime) are installed too, each with a link in
# /usr/bin. Every program has an entry in the desktop's application menu, all in one folder,
# Parley (as the Windows installer's Start menu folder): fsh and fshdb open in a terminal, fsh-ide
# and parley-files-viewer are window programs. Their icon is linux/icons: Parley's tower with the
# >_ badge (as macos/fsh.icns and windows/fsh.ico).
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

# the other programs the jlink build has
apps=""
for a in fsh-ide fshdb parley-files-viewer; do
	[ -x "$image/bin/$a" ] && apps="$apps $a"
done

summary="fsh, the FileSource Shell"
about="A bash-like shell whose file system is any parley-files FileSource: the local disk, memory, FTP, SFTP or a database. It carries its own Java runtime."
if [ -n "$apps" ]; then
	about="$about With it, on the same runtime:"
	for a in $apps; do
		case $a in
		fsh-ide) about="$about fsh-ide, an editor and debugger for shell scripts;" ;;
		fshdb) about="$about fshdb, a debugger for shell scripts that runs in a terminal;" ;;
		parley-files-viewer) about="$about parley-files-viewer, a file browser for any file system;" ;;
		esac
	done
	about="${about%;}."
	echo "with:$apps"
fi
url=https://github.com/parley-project/parley-fsh
maintainer="Tony Bringardner <tony.bringardner@gmail.com>"
work=$PROJECT_DIR/target/linux-packages
rm -rf "$work"
mkdir -p dist

# the files both install: /opt/fsh (the jlink build), the links in /usr/bin, and the menu
root=$work/root
mkdir -p "$root/opt" "$root/usr/bin"
cp -Rp "$image" "$root/opt/fsh"
for a in fsh $apps; do
	ln -s /opt/fsh/bin/$a "$root/usr/bin/$a"
done
chmod -R u=rwX,go=rX "$root/opt/fsh"

# The application menu (freedesktop.org): an entry for each program, in the Parley folder
# (parley.menu merged into the desktop's menu; the folder's name and icon in parley.directory).
# Entries in no other category, so they are in that folder only.
apps_dir=$root/usr/share/applications
mkdir -p "$apps_dir" "$root/usr/share/desktop-directories" "$root/etc/xdg/menus/applications-merged"
for s in 16 32 48 64 128 256 512; do
	mkdir -p "$root/usr/share/icons/hicolor/${s}x$s/apps"
	cp -p "linux/icons/parley-icon-terminal-$s.png" "$root/usr/share/icons/hicolor/${s}x$s/apps/parley-fsh.png"
done
desktop() {
	# $1 the program, $2 its menu name, $3 its comment, $4 true for a terminal program
	{
		echo '[Desktop Entry]'
		echo 'Type=Application'
		echo "Name=$2"
		echo "Comment=$3"
		echo "Exec=/usr/bin/$1"
		echo "TryExec=/usr/bin/$1"
		echo 'Icon=parley-fsh'
		echo "Terminal=$4"
		echo 'Categories=X-Parley;'
		# (a window program's windows grouped with its entry: AWT's class name for them)
		case $1 in
		fsh-ide) echo 'StartupWMClass=us-bringardner-fsh-ide-FshIDE' ;;
		parley-files-viewer) echo 'StartupWMClass=us-bringardner-parley-files-viewer-FileSourceViewer' ;;
		esac
	} > "$apps_dir/$(desktop_file $1)"
}
# (parley-NAME.desktop, or NAME.desktop when the name is parley-...)
desktop_file() {
	case $1 in
	parley-*) echo "$1.desktop" ;;
	*) echo "parley-$1.desktop" ;;
	esac
}
desktop fsh fsh "fsh, the FileSource Shell: a bash-like shell on any parley-files file system" true
for a in $apps; do
	case $a in
	fsh-ide) desktop fsh-ide fsh-ide "fsh-ide, an editor and debugger for shell scripts" false ;;
	fshdb) desktop fshdb fshdb "fshdb, a debugger for shell scripts that runs in a terminal" true ;;
	parley-files-viewer) desktop parley-files-viewer parley-files-viewer "parley-files-viewer, a file browser for any file system" false ;;
	esac
done
cat > "$root/usr/share/desktop-directories/parley.directory" <<'DIR'
[Desktop Entry]
Type=Directory
Name=Parley
Icon=parley-fsh
DIR
cat > "$root/etc/xdg/menus/applications-merged/parley.menu" <<'MENU'
<!DOCTYPE Menu PUBLIC "-//freedesktop//DTD Menu 1.0//EN"
 "http://www.freedesktop.org/standards/menu-spec/menu-1.0.dtd">
<Menu>
	<Name>Applications</Name>
	<Menu>
		<Name>Parley</Name>
		<Directory>parley.directory</Directory>
		<Include>
			<Category>X-Parley</Category>
		</Include>
	</Menu>
</Menu>
MENU
chmod -R u=rwX,go=rX "$root/usr/share" "$root/etc"

# (/etc/shells: added after installing, taken out after removing, not after an upgrade)
add_shell='if [ -f /etc/shells ] && ! grep -qx /usr/bin/fsh /etc/shells; then echo /usr/bin/fsh >> /etc/shells; fi'
remove_shell='if [ -f /etc/shells ]; then grep -vx /usr/bin/fsh /etc/shells > /etc/shells.fsh-tmp || true; cat /etc/shells.fsh-tmp > /etc/shells; rm -f /etc/shells.fsh-tmp; fi'
# (the desktop's caches of the menu entries and icons, where it has them)
update_menu='if command -v update-desktop-database > /dev/null 2>&1; then update-desktop-database -q /usr/share/applications || true; fi; if command -v gtk-update-icon-cache > /dev/null 2>&1; then gtk-update-icon-cache -q -t -f /usr/share/icons/hicolor || true; fi'
# (the window programs need the X libraries, which a server without a desktop may not have:
# recommended, not required)
deb_recommends=""
rpm_recommends=""
case "$apps" in
*fsh-ide*|*parley-files-viewer*)
	deb_recommends="Recommends: libxext6, libxrender1, libxtst6, libxi6, fontconfig"
	rpm_recommends="Recommends: libXext libXrender libXtst libXi fontconfig" ;;
esac

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
		{
			cat <<CTL
Package: fsh
Version: $version
Architecture: $deb_arch
Maintainer: $maintainer
Section: shells
Priority: optional
Installed-Size: $(du -sk "$root" | cut -f1)
Homepage: $url
CTL
			[ -n "$deb_recommends" ] && echo "$deb_recommends"
			cat <<CTL
Description: $summary
 $about
CTL
		} > "$deb/DEBIAN/control"
		printf '#!/bin/sh\nset -e\nif [ "$1" = configure ]; then\n\t%s\nfi\n%s\n' "$add_shell" "$update_menu" > "$deb/DEBIAN/postinst"
		printf '#!/bin/sh\nset -e\nif [ "$1" = remove ] || [ "$1" = purge ]; then\n\t%s\nfi\n%s\n' "$remove_shell" "$update_menu" > "$deb/DEBIAN/postrm"
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
$rpm_recommends

%description
$about

%install
mkdir -p %{buildroot}
cp -Rp $root/. %{buildroot}/

%post
$add_shell
$update_menu

%postun
if [ "\$1" = 0 ]; then
	$remove_shell
fi
$update_menu

%files
%defattr(-,root,root,-)
/opt/fsh
$(for a in fsh $apps; do echo "/usr/bin/$a"; done)
/usr/share/applications/parley-*.desktop
/usr/share/desktop-directories/parley.directory
/usr/share/icons/hicolor/*/apps/parley-fsh.png
/etc/xdg/menus/applications-merged/parley.menu
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
