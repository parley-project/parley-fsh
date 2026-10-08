# needs: seq cat wc tr sort head
# a program that does not read its input leaves it for the shell (bash shares the file)
seq 3 | while read l; do /bin/echo "l=$l"; done
printf "a\nb\nc\n" > f
while read l; do /bin/echo "f=$l"; done < f
while read l; do /bin/echo "h=$l"; done <<EOF2
one
two
EOF2
while read l; do /bin/echo "s=$l"; done <<< "only"
# a program that reads it all leaves nothing
seq 4 | { read a; cat; read b; echo "a=$a b=${b:-none}"; }
while read l; do /bin/echo "c=$l"; cat > /dev/null; done < f
# a program that reads some of a pipe
seq 3 | { read x; tr a-z A-Z; } | wc -l | tr -d ' '
printf "3\n1\n2\n" | while read n; do /bin/cat </dev/null; echo "n=$n"; done | sort
