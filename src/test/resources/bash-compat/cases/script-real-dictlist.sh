# needs: sort
declare -A groups
add() { groups[$1]+="${groups[$1]:+,}$2"; }
add fruit apple; add veg carrot; add fruit pear; add fruit "kiwi"; add veg leek
for g in $(printf '%s\n' "${!groups[@]}" | sort); do IFS=, read -ra items <<< "${groups[$g]}"; echo "$g (${#items[@]}): ${items[*]}"; done
