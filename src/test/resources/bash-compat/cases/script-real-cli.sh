# needs: head
VERSION=1.2.0
usage() { cat <<USAGE
Usage: tool <command> [options]
Commands:
  add <a> <b>     add two numbers
  greet [name]    say hello
  version         print the version
USAGE
}
cmd=${1:-help}; shift || true
main() {
  case $1 in
    add) [[ $# -eq 3 ]] || { echo "add needs 2 args" >&2; return 2; }; echo $(( $2 + $3 ));;
    greet) echo "Hello, ${2:-stranger}!";;
    version|-v|--version) echo "tool $VERSION";;
    help|-h|--help) usage;;
    *) echo "unknown command: $1" >&2; usage >&2; return 1;;
  esac
}
for args in "add 2 40" "greet" "greet Ann" "version" "help" "bogus"; do main $args; echo "rc=$?"; done 2>&1 | head -20
