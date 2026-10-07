f(){ local n=$1 to=$2; [ $n -gt 0 ] && f $((n-1)) X; echo "$n $to"; }; f 1 Y; echo "[${to-unset}]"; g(){ local w=5 p=$((w*2)) q=$w; echo "[$p][$q]"; }; w=1; g
