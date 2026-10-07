declare -A A B C; n=3
for ((i=0;i<n;i++)); do for ((j=0;j<n;j++)); do A[$i,$j]=$((i+j)); B[$i,$j]=$(( i==j ? 2 : 0 )); done; done
for ((i=0;i<n;i++)); do for ((j=0;j<n;j++)); do s=0; for ((k=0;k<n;k++)); do ((s += A[$i,$k] * B[$k,$j])); done; C[$i,$j]=$s; done; done
for ((i=0;i<n;i++)); do row=(); for ((j=0;j<n;j++)); do row+=("${C[$i,$j]}"); done; printf '%3s' "${row[@]}"; echo; done
trace=0; for ((i=0;i<n;i++)); do ((trace+=C[$i,$i])); done; echo "trace=$trace keys=${#C[@]}"
