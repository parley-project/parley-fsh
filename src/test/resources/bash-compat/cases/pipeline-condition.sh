# needs: grep cat
if echo x | grep -q x; then echo y1; fi; if ! echo x | grep -q z; then echo y2; fi
if grep -q a <<< abc | cat; then echo y3; fi; n=0; while echo go | grep -q go && (( n < 2 )); do ((n++)); done; echo n=$n
until echo stop | grep -q stop; do :; done; echo until-done
