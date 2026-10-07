arr=(42 7 19 3 88 23 5)
bubble() { local -n A=$1; local n=${#A[@]} i j t; for ((i=0;i<n;i++)); do for ((j=0;j<n-i-1;j++)); do if (( A[j] > A[j+1] )); then t=${A[j]}; A[j]=${A[j+1]}; A[j+1]=$t; fi; done; done; }
bubble arr; echo "sorted: ${arr[*]}"
bsearch() { local -n A=$1; local x=$2 lo=0 hi=$(( ${#A[@]} - 1 )) mid; while (( lo <= hi )); do mid=$(( (lo+hi)/2 )); if (( A[mid] == x )); then echo $mid; return 0; elif (( A[mid] < x )); then lo=$((mid+1)); else hi=$((mid-1)); fi; done; echo -1; return 1; }
for x in 23 3 88 50; do echo "$x at $(bsearch arr $x)"; done
