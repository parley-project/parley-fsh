# needs: tr bc
rows=("name|qty|price" "apple|3|0.5" "banana|12|0.25" "cherry|100|0.1")
widths=(0 0 0)
for r in "${rows[@]}"; do IFS='|' read -ra c <<< "$r"; for i in 0 1 2; do (( ${#c[i]} > widths[i] )) && widths[i]=${#c[i]}; done; done
sep=$(printf '%*s' $((widths[0]+widths[1]+widths[2]+6)) '' | tr ' ' -)
for r in "${rows[@]}"; do IFS='|' read -r a b c <<< "$r"; printf "%-${widths[0]}s | %${widths[1]}s | %${widths[2]}s\n" "$a" "$b" "$c"; [[ $a == name ]] && echo "$sep"; done
total=0; for r in "${rows[@]:1}"; do IFS='|' read -r _ q p <<< "$r"; total=$(echo "$total + $q * $p" | bc); done; echo "total=$total"
