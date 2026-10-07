# needs: grep
export EX1=v; export EX2="a b"; EX3=q; export EX3; export -p | grep " EX"; export -n EX1; export -p | grep -c EX1
