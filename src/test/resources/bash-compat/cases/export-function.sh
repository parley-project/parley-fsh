# needs: bash
f() { echo "in f $1"; local x=1; echo "x=$x"; }
export -f f
bash -c 'f arg'
export -f | grep -c 'declare -fx f'
export -fn f
bash -c 'f' 2>/dev/null || echo "not exported"
type -t f
