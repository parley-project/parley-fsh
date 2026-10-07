# needs: sort tail cut
data='alice,30,NY
bob,25,LA
carol,35,NY
dave,40,SF'
declare -A count sum
while IFS=, read -r name age city; do
  (( count[$city]++ )) || true
  (( sum[$city] += age ))
done <<< "$data"
printf '%-4s %5s %6s\n' CITY N AVG
for c in $(printf '%s\n' "${!count[@]}" | sort); do
  printf '%-4s %5d %6.1f\n' "$c" "${count[$c]}" "$(( sum[$c] * 10 / count[$c] ))e-1"
done
oldest=$(sort -t, -k2 -n <<< "$data" | tail -1 | cut -d, -f1)
echo "oldest: $oldest"
