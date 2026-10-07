pass=0 fail=0
assert_eq() { if [[ "$1" == "$2" ]]; then ((pass++)); else ((fail++)); printf 'FAIL %s: expected [%s] got [%s]\n' "${3:-test}" "$2" "$1"; fi; }
assert_true() { if "$@"; then ((pass++)); else ((fail++)); echo "FAIL: $*"; fi; }
upper() { echo "${1^^}"; }
assert_eq "$(upper abc)" ABC upper
assert_eq "$((6*7))" 42 math
assert_eq "$(upper x)" Y deliberate
assert_true test -n "x"
assert_true [ 1 -lt 2 ]
assert_true false
printf 'passed=%d failed=%d\n' "$pass" "$fail"
(( fail == 2 )) && echo "expected failures"
