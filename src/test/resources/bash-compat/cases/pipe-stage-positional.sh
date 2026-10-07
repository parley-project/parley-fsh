set -- a b c; echo | { set -- z; echo "$1 $#"; }; echo "$1 $#"
f() { echo | { shift; echo "in $1"; }; echo "out $1"; }; f p q
