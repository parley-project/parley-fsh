printf "a\0b c\0" | while IFS= read -r -d "" x; do echo "[$x]"; done
printf "p:q:r" | { read -d : first; echo $first; }
