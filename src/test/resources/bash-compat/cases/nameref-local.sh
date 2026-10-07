setv() { local -n r=$1; r="set by func"; }; setv v; echo "$v"; arr=(a b); addv() { local -n a=$1; a+=(c); }; addv arr; echo ${arr[@]}
