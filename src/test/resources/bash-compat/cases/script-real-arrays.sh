# needs: sort
nums=(5 3 9 1 7)
sorted=($(printf '%s\n' "${nums[@]}" | sort -n))
echo "sorted: ${sorted[*]} min=${sorted[0]} max=${sorted[-1]}"
sum=0; for n in "${nums[@]}"; do (( sum += n )); done; echo "sum=$sum avg=$(( sum / ${#nums[@]} ))"
evens=(); for n in "${nums[@]}"; do (( n % 2 == 0 )) || evens+=("$n"); done; echo "odd: ${evens[*]}"
declare -a stack=(); push(){ stack+=("$1"); }; pop(){ local top=${stack[-1]}; unset 'stack[-1]'; echo "$top"; }
push a; push "b c"; push d; pop; pop; echo "left: ${#stack[@]} ${stack[0]}"
joined=$(IFS=:; echo "${nums[*]}"); echo "$joined"
mapfile -t lines < <(printf 'x\ny\nz\n'); echo "${#lines[@]} ${lines[1]}"
contains() { local e; for e in "${@:2}"; do [[ $e == "$1" ]] && return 0; done; return 1; }
contains 9 "${nums[@]}" && echo "has 9"; contains 4 "${nums[@]}" || echo "no 4"
