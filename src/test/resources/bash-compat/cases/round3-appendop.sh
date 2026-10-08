# += in temporary assignments, integer arrays and [i]+=v in array literals
a=1; a+=4; echo $a
a+=5 sh -c 'echo $a'
typeset -i x; x=(1 2 3 4 5); x[4]+=7; echo ${x[@]}
x=( 1 2 [2]+=7 4 5 ); echo ${x[@]}
x+=( [3]+=9 [5]=9 ); echo ${x[@]}
unset a; a=1; export a+=4; sh -c 'echo $a'
declare -A m=([k]=v); m+=zero; declare -p m
