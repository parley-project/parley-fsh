f() { local n=$1; [ $n -le 0 ] && return; echo $n; f $((n-1)); }; f 3
