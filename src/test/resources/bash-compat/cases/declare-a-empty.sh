declare -a st=(); st+=(a "b c"); unset "st[-1]"; echo ${#st[@]} ${st[0]}; local_test(){ local -a q=(); q+=(1); echo ${#q[@]}; }; local_test; declare -a z=([2]=x); echo ${!z[@]}
