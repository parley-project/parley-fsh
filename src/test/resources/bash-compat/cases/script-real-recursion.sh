# needs: tr wc
moves=0
hanoi() { local n=$1 from=$2 to=$3 via=$4; ((n==0)) && return; hanoi $((n-1)) $from $via $to; ((moves++)); [[ $n -eq 3 ]] && echo "move disk 3 $from->$to"; hanoi $((n-1)) $via $to $from; }
hanoi 4 A C B; echo "moves=$moves"
permute() { local prefix=$1 rest=$2 i; [[ -z $rest ]] && { echo "$prefix"; return; }; for ((i=0;i<${#rest};i++)); do permute "$prefix${rest:i:1}" "${rest:0:i}${rest:i+1}"; done; }
permute "" abc | tr '\n' ' '; echo
count=$(permute "" abcd | wc -l | tr -d ' '); echo "4! = $count"
