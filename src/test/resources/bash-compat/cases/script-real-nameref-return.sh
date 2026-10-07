split_kv() { local -n _k=$2 _v=$3; _k=${1%%=*}; _v=${1#*=}; }
split_kv "host=example.com" key val; echo "$key -> $val"
stats() { local -n out=$1; shift; local min=$1 max=$1 sum=0 n; for n; do ((n<min)) && min=$n; ((n>max)) && max=$n; ((sum+=n)); done; out=([min]=$min [max]=$max [avg]=$((sum/$#))); }
declare -A s; stats s 4 8 15 16 23 42; echo "min=${s[min]} max=${s[max]} avg=${s[avg]}"
append_to() { local -n arr=$1; shift; arr+=("$@"); }
list=(a); append_to list b "c d"; echo "${#list[@]}: ${list[2]}"
