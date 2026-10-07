# needs: cat
n=3; cat <<EOF
count: $n
sum: $((n+1))
cmd: $(echo hi)
EOF
