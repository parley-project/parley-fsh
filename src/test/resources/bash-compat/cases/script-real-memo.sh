declare -A memo
fib() { local n=$1; if [[ -n ${memo[$n]:-} ]]; then echo "${memo[$n]}"; return; fi
  if (( n < 2 )); then memo[$n]=$n; else local a b; a=$(fib $((n-1))); b=$(fib $((n-2))); memo[$n]=$((a+b)); fi; echo "${memo[$n]}"; }
fib_iter() { local a=0 b=1 i; for ((i=0;i<$1;i++)); do ((b=a+b, a=b-a)); done; echo $a; }
echo "fib 15 = $(fib 15)  iter 50 = $(fib_iter 50)"
echo "memo entries in subshell call: ${#memo[@]}"
