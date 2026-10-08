exec 2>&1
set -x
x=1 y="a b"
x=2 echo hi
echo '*' "it's" "" a=b '~x' "$y"
f() { echo "in f" "$1"; }
f 'q r'
y=$(true)
set +x
echo done
