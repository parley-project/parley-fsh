# needs: ls grep
set -eE
trap 'echo "error on line $LINENO: $BASH_COMMAND (rc=$?)"' ERR
trap 'echo "exit rc=$?"' EXIT
step() { echo "step $1"; }
step 1
false || echo "handled"
if ! grep -q nothing /dev/null; then echo "not found ok"; fi
x=$(echo fine); echo "x=$x"
step 2
ls /no/such/dir 2>/dev/null
step 3
