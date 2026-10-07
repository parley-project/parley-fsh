# needs: sort tail
vercmp() { local IFS=.; local -a a=($1) b=($2); local i
  for ((i=0; i<3; i++)); do local x=${a[i]:-0} y=${b[i]:-0}; ((10#$x > 10#$y)) && { echo 1; return; }; ((10#$x < 10#$y)) && { echo -1; return; }; done; echo 0; }
for pair in "1.2.3 1.2.3" "1.10.0 1.9.9" "2.0 2.0.1" "0.9.12 0.10.0"; do set -- $pair; r=$(vercmp $1 $2); case $r in 1) s='>';; -1) s='<';; 0) s='=';; esac; echo "$1 $s $2"; done
latest=$(printf '%s\n' 1.2.0 1.10.1 1.9.5 | sort -t. -k1,1n -k2,2n -k3,3n | tail -1); echo "latest=$latest"
