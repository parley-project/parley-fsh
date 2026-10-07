# needs: sort head
text="the quick brown fox jumps over the lazy dog the fox"
declare -A freq
for w in $text; do (( freq[$w]++ )); done
for w in "${!freq[@]}"; do printf '%d %s\n' "${freq[$w]}" "$w"; done | sort -k1,1nr -k2 | head -3
echo "distinct: ${#freq[@]}"
longest=""; for w in "${!freq[@]}"; do (( ${#w} > ${#longest} )) && longest=$w; done; echo "longest has ${#longest} letters"
