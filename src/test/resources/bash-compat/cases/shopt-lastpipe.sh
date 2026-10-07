shopt -s lastpipe; n=0; printf "a\nb\n" | while read l; do n=$((n+1)); done; echo $n; echo hi | read v; echo $v
