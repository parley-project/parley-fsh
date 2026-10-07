sleep 1 | { read -t 0.2 x; echo $? "[$x]"; }
read -t 0 x <<< hi; echo $?
read -t 1 y <<< val; echo $? $y
read -t 2 z < /dev/null; echo $?
