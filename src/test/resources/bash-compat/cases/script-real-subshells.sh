# needs: tr
x=1
( x=2; echo "in subshell $x" ); echo "after $x"
y=$( x=3; echo $x ); echo "y=$y x=$x"
{ x=4; }; echo "group x=$x"
echo hi | { read -r v; echo "pipe got $v"; }; echo "v=[${v-}]"
out=$( { echo out; echo err >&2; } 2>&1 ); echo "$out" | tr '\n' ' '; echo
res=$(exit 7); echo "status $?"
f() { echo "f says $1"; return 4; }; val=$(f arg); rc=$?; echo "$val rc=$rc"
