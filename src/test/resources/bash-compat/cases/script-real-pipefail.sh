# needs: grep cat
run() { "$@"; local rc=$?; echo "[$*] rc=$rc"; return $rc; }
run true
run false || echo "handled"
set -o pipefail
false | true; echo "pipe rc=$?"
true | true; echo "pipe rc=$?"
if ! grep -q zzz <<< "abc" | cat; then echo "no zzz"; fi
out=$(echo data | grep -o dat) && echo "got $out"
set +o pipefail; false | true; echo "no pipefail rc=$?"
