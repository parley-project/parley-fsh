printf "a:1\nb:2\n" | while IFS=: read k v; do echo "$k=$v"; done
