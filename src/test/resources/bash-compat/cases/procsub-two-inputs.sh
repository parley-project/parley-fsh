# needs: diff
diff <(printf '1\n2\n') <(printf '1\n3\n'); echo "diff=$?"
cat <(echo one) <(echo two)
mapfile -t arr < <(printf 'a\nb\nc\n'); echo "${#arr[@]} ${arr[2]}"
paste -d, <(printf 'x\ny\n') <(printf '1\n2\n')
