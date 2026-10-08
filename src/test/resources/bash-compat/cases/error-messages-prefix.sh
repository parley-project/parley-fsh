# bash's messages say where: script: line N: ... (the script's name is cut off here)
where() { sed 's/^.*\(line [0-9]*:\)/\1/'; }
{ cd /nonexistent; } 2>&1 | where
{ nosuchcommand; } 2>&1 | where
{ echo hi > /nonexistent/x; } 2>&1 | where
{ : $((1/0)); } 2>&1 | where
{ [ a -eq ]; } 2>&1 | where
{ read -t x v; } 2>&1 | where
{ printf '%d\n' abc; } 2>&1 | where
{ echo ${x y}; } 2>&1 | where
{ shopt -s nosuchopt; } 2>&1 | where
( set -u; echo $undefined_var ) 2>&1 | where
{ readonly ro=1; ro=2; } 2>&1 | where
