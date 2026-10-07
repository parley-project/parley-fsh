declare -A m=([a b]=1 [c]=2); unset "m[a b]"; echo ${#m[@]} ${!m[@]}
