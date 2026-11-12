#!/bin/sh
# The macOS and Linux installers with the Lanterna browser plugin built in (fsh has its own file
# browser: browse, view, edit, search), and fshdb beside fsh, as tools/build-msi-with-plugins.ps1
# makes the Windows one: builds parley-files-viewer-tui (the file browser fsh builds in, then the
# Lanterna plugin) and names the plugin in FSH_PLUGINS, installs what fshdb builds against, then
# runs tools/build-jlink.sh with it and tools/build-macos-pkg.sh (macOS) or
# tools/build-linux-packages.sh (Linux).
#
# The installers hold commands only, fsh and fshdb. The window programs, fsh-ide and
# parley-files-viewer, have installers of their own (parley-build's packaging).
#
# Usage: tools/build-installers-with-plugins.sh [OPTIONS] [TYPE] [VERSION]
#   TYPE                macOS: pkg (the default); Linux: deb, rpm or all (the default); none: only
#                       the jlink build (target/jlink/fsh)
#   VERSION             the pom's, without -SNAPSHOT
#   --plugins-repo DIR  a checkout of parley-files-viewer-tui (default: beside this one)
#   --db-repo DIR       a checkout of fshdb, the console debugger (default: beside this one;
#                       --no-db leaves it out)
#   --ide-repo DIR      a checkout of fsh-ide, which fshdb builds against, so it is installed to
#                       the local Maven repository first (default: beside this one). fsh-ide
#                       itself is not in the installers.
#   --loupe-repo DIR    a checkout of fsh-loupe, which fsh-ide needs (default: beside this one)
#
# Needs what those scripts need (a JDK in JAVA_HOME; dpkg-deb and rpmbuild on Linux), and Maven.
# fsh builds against the file browser, and the plugin and fshdb build against fsh, so each is
# installed to the local Maven repository first (tests skipped).

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
BESIDE=$(dirname "$PROJECT_DIR")

plugins_repo=""
loupe_repo=""
ide_repo=""
db_repo=""
no_db=""
type=""
version=""
while [ $# -gt 0 ]; do
	case $1 in
	--plugins-repo|--loupe-repo|--ide-repo|--db-repo)
		[ -n "$2" ] || { echo "$0: $1 needs a folder" >&2; exit 2; }
		case $1 in
		--plugins-repo) plugins_repo=$2 ;;
		--loupe-repo) loupe_repo=$2 ;;
		--ide-repo) ide_repo=$2 ;;
		--db-repo) db_repo=$2 ;;
		esac
		shift ;;
	--no-db) no_db=yes ;;
	-*) echo "$0: unknown option $1 (see the comment at the top)" >&2; exit 2 ;;
	*)
		if [ -z "$type" ]; then
			type=$1
		elif [ -z "$version" ]; then
			version=$1
		else
			echo "$0: too many arguments" >&2
			exit 2
		fi ;;
	esac
	shift
done

case $(uname -s) in
Darwin) package=tools/build-macos-pkg.sh; type=${type:-pkg} ;;
Linux) package=tools/build-linux-packages.sh; type=${type:-all} ;;
*) echo "$0: macOS and Linux only (Windows: tools/build-msi-with-plugins.ps1)" >&2; exit 1 ;;
esac
case $package:$type in
tools/build-macos-pkg.sh:pkg|tools/build-linux-packages.sh:deb|tools/build-linux-packages.sh:rpm|tools/build-linux-packages.sh:all|*:none) ;;
*) echo "$0: unknown type $type (see the comment at the top)" >&2; exit 2 ;;
esac

# a checkout's absolute path, or why there is none
checkout() {
	# $1 the folder, $2 what it is, $3 the option that names it
	if [ ! -f "$1/pom.xml" ]; then
		echo "$0: no $2 checkout at $1 ($3)" >&2
		exit 1
	fi
	(cd "$1" && pwd)
}
plugins_repo=$(checkout "${plugins_repo:-$BESIDE/parley-files-viewer-tui}" parley-files-viewer-tui --plugins-repo) || exit 1
if [ -n "$no_db" ]; then
	db_repo=""
else
	db_repo=$(checkout "${db_repo:-$BESIDE/fshdb}" fshdb "--db-repo, or --no-db") || exit 1
	# (fshdb is built against fsh-ide, and fsh-ide against fsh-loupe)
	ide_repo=$(checkout "${ide_repo:-$BESIDE/fsh-ide}" fsh-ide "--ide-repo: fshdb builds against it; or --no-db") || exit 1
fi

install_module() {
	# $1 the checkout, $2 what it is
	if ! (cd "$1" && mvn -B -q -DskipTests install); then
		echo "$0: $2 build failed" >&2
		exit 1
	fi
}

cd "$PROJECT_DIR"
# (the file browser fsh builds in: its library, before fsh)
if ! (cd "$plugins_repo" && mvn -B -q -DskipTests -pl parley-files-viewer-tui install); then
	echo "$0: file browser build failed" >&2
	exit 1
fi
install_module "$PROJECT_DIR" fsh

# the Lanterna plugin (browse --with lanterna), built against fsh: not the -all jar, which runs alone
if ! (cd "$plugins_repo" && mvn -B -q -DskipTests -pl parley-files-viewer-tui-lanterna package); then
	echo "$0: plugin build failed" >&2
	exit 1
fi
FSH_PLUGINS=$(ls "$plugins_repo"/parley-files-viewer-tui-lanterna/target/parley-files-viewer-tui-lanterna-*-plugin.jar 2> /dev/null \
	| grep -Ev -- '-(all|sources|javadoc|tests)\.jar$' | tr '\n' ' ' || true)
if [ -z "$FSH_PLUGINS" ]; then
	echo "$0: expected the lanterna plugin jar under $plugins_repo" >&2
	exit 1
fi
export FSH_PLUGINS

if [ -n "$db_repo" ]; then
	# (fsh-ide's dependency, built against fsh: not in a Maven repository until it is installed)
	loupe=${loupe_repo:-$BESIDE/fsh-loupe}
	if [ -f "$loupe/pom.xml" ]; then
		install_module "$loupe" fsh-loupe
	fi
	# (fshdb is built against fsh-ide)
	install_module "$ide_repo" fsh-ide
fi

set --
if [ -n "$db_repo" ]; then set -- "$@" --db "$db_repo"; fi
tools/build-jlink.sh "$@"

if [ "$type" = none ]; then
	exit 0
fi
# (the macOS script makes one type, so it takes only the version)
case $package in
tools/build-macos-pkg.sh) set -- ;;
*) set -- "$type" ;;
esac
if [ -n "$version" ]; then set -- "$@" "$version"; fi
"$package" "$@"
