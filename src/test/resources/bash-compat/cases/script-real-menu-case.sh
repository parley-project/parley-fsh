handle() {
  case ${1,,} in
    start|run) echo "starting";;
    stop) echo "stopping";;
    restart) handle stop; handle start;;
    status*) echo "status: ${1#status}";;
    [0-9]*) echo "number $1";;
    '') echo "empty";;
    *) echo "unknown: $1"; return 1;;
  esac
}
for c in START restart status-x 42 "" bogus; do handle "$c" || echo "rc=$?"; done
