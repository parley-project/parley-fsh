declare -A m=([a]=2 [b c]=5); echo $(( m[a] * 3 )); k=a; echo $(( m[$k] + 1 ))
