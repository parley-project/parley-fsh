attempt=0
flaky() { (( ++attempt < 3 )) && { echo "try $attempt failed" >&2; return 1; }; echo "ok on $attempt"; }
retry() {
  local n=$1 delay=$2; shift 2
  local i
  for ((i=1; i<=n; i++)); do
    "$@" && return 0
    sleep "$delay"
  done
  return 1
}
retry 5 0 flaky 2>&1
retry 2 0 false || echo "gave up: $?"
