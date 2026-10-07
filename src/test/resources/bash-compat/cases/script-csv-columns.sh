printf "a,b,c\n1,2,3\n" | while IFS=, read -r x y z; do echo "$z $y $x"; done
