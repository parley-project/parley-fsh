# needs: cat sort
text="the cat and the hat and the bat"; declare -A n; for w in $text; do n[$w]=$(( ${n[$w]:-0} + 1 )); done; for k in $(printf "%s\n" "${!n[@]}" | sort); do echo "$k ${n[$k]}"; done
