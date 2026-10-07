f() { local n=$1; if (( n <= 1 )); then echo 1; else echo $(( n * $(f $((n-1))) )); fi; }; f 5
