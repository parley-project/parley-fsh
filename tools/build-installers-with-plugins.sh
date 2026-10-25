#!/bin/sh
# The macOS and Linux installers with the file browser plugin (browse, view, edit, search) built
# in, and fsh-ide, fshdb and parley-files-viewer beside fsh, as tools/build-msi-with-plugins.ps1
# makes the Windows one: builds the jars of parley-files-viewer-tui and names them in FSH_PLUGINS,
# installs what the other programs build against, then runs tools/build-jlink.sh with them and
# tools/build-jpackage.sh (macOS) or tools/build-linux-packages.sh (Linux).
#
# Usage: tools/build-installers-with-plugins.sh [OPTIONS] [TYPE] [VERSION]
#   TYPE                macOS: dmg (the default), pkg or app-image; Linux: deb, rpm or all (the
#                       default); none: only the jlink build (target/jlink/fsh)
#   VERSION             the pom's, without -SNAPSHOT
#   --plugins-repo DIR  a checkout of parley-files-viewer-tui (default: beside this one)
#   --ide-repo DIR      a checkout of fsh-ide, the Swing IDE (default: beside this one; --no-ide
#                       leaves it out). fsh-loupe, beside this one too, is installed first.
#   --db-repo DIR       a checkout of fshdb, the console debugger (default: beside this one;
#                       --no-db leaves it out). It builds against fsh-ide, so that is installed first.
#   --loupe-repo DIR    a checkout of fsh-loupe, which fsh-ide needs (default: beside this one)
#   --viewer-repo DIR   a checkout of parley-files-viewer, the Swing file browser (default: beside
#                       this one; --no-viewer leaves it out)
#
# Needs what those scripts need (a JDK in JAVA_HOME; dpkg-deb and rpmbuild on Linux), and Maven.
# The plugin and the programs build against fsh, so fsh is installed to the local Maven
# repository first (tests skipped).

set -e
PROJECT_DIR=$(cd "$(dirname "$0")/.." && pwd)
BESIDE=$(dirname "$PROJECT_DIR")

plugins_repo=""
loupe_repo=""
ide_repo=""
db_repo=""
viewer_repo=""
no_ide=""
no_db=""
no_viewer=""
type=""
version=""
while [ $# -gt 0 ]; do
	case $1 in
	--plugins-repo|--loupe-repo|--ide-repo|--db-repo|--viewer-repo)
		[ -n "$2" ] || { echo "$0: $1 needs a folder" >&2; exit 2; }
		case $1 in
		--plugins-repo) plugins_repo=$2 ;;
		--loupe-repo) loupe_repo=$2 ;;
		--ide-repo) ide_repo=$2 ;;
		--db-repo) db_repo=$2 ;;
		--viewer-repo) viewer_repo=$2 ;;
		esac
		shift ;;
	--no-ide) no_ide=yes ;;
	--no-db) no_db=yes ;;
	--no-viewer) no_viewer=yes ;;
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
Darwin) package=tools/build-jpackage.sh; type=${type:-dmg} ;;
Linux) package=tools/build-linux-packages.sh; type=${type:-all} ;;
*) echo "$0: macOS and Linux only (Windows: tools/build-msi-with-plugins.ps1)" >&2; exit 1 ;;
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
if [ -n "$no_ide" ]; then
	ide_repo=""
else
	ide_repo=$(checkout "${ide_repo:-$BESIDE/fsh-ide}" fsh-ide "--ide-repo, or --no-ide") || exit 1
fi
if [ -n "$no_db" ]; then
	db_repo=""
else
	db_repo=$(checkout "${db_repo:-$BESIDE/fshdb}" fshdb "--db-repo, or --no-db") || exit 1
fi
if [ -n "$no_viewer" ]; then
	viewer_repo=""
else
	viewer_repo=$(checkout "${viewer_repo:-$BESIDE/parley-files-viewer}" parley-files-viewer "--viewer-repo, or --no-viewer") || exit 1
fi

install_module() {
	# $1 the checkout, $2 what it is
	if ! (cd "$1" && mvn -B -q -DskipTests install); then
		echo "$0: $2 build failed" >&2
		exit 1
	fi
}

cd "$PROJECT_DIR"
install_module "$PROJECT_DIR" fsh

# the plain jar and the Lanterna one (browse --with lanterna): not the -all jars, which run alone
if ! (cd "$plugins_repo" && mvn -B -q -DskipTests package); then
	echo "$0: plugin build failed" >&2
	exit 1
fi
FSH_PLUGINS=$(ls "$plugins_repo"/parley-files-viewer-tui/target/parley-files-viewer-tui-*.jar \
	"$plugins_repo"/parley-files-viewer-tui-lanterna/target/parley-files-viewer-tui-lanterna-*-plugin.jar 2> /dev/null \
	| grep -Ev -- '-(all|sources|javadoc|tests)\.jar$' | tr '\n' ' ' || true)
if [ "$(echo $FSH_PLUGINS | wc -w)" -lt 2 ]; then
	echo "$0: expected the tui and lanterna plugin jars under $plugins_repo, found: $FSH_PLUGINS" >&2
	exit 1
fi
export FSH_PLUGINS

if [ -n "$ide_repo" ] || [ -n "$db_repo" ]; then
	# (fsh-ide's dependency, built against fsh: not in a Maven repository until it is installed)
	loupe=${loupe_repo:-$BESIDE/fsh-loupe}
	if [ -f "$loupe/pom.xml" ]; then
		install_module "$loupe" fsh-loupe
	fi
fi
if [ -n "$db_repo" ]; then
	# (fshdb is built against fsh-ide, wherever that comes from)
	ide=${ide_repo:-$BESIDE/fsh-ide}
	if [ -f "$ide/pom.xml" ]; then
		install_module "$ide" fsh-ide
	fi
fi

set --
if [ -n "$ide_repo" ]; then set -- "$@" --ide "$ide_repo"; fi
if [ -n "$db_repo" ]; then set -- "$@" --db "$db_repo"; fi
if [ -n "$viewer_repo" ]; then set -- "$@" --viewer "$viewer_repo"; fi
tools/build-jlink.sh "$@"

if [ "$type" = none ]; then
	exit 0
fi
if [ -n "$version" ]; then
	"$package" "$type" "$version"
else
	"$package" "$type"
fi
