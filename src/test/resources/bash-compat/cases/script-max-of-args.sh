max() { local m=$1; shift; for n; do (( n > m )) && m=$n; done; echo $m; }; max 3 9 2 7
