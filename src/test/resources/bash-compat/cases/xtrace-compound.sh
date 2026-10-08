exec 2>&1
set -x
x=3
[[ $x == 3 && -n "a b" ]] && (( x > 1 ))
for i in a "b c"; do :; done
case $x in 3) : ;; esac
for ((i=0; i<1; i++)); do :; done
set +x
