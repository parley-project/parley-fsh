export TZ=UTC
user=ann; items=(apple "pear tart" fig)
total=0; for i in "${!items[@]}"; do total=$((total + ${#items[i]})); done
cat <<EOT
Dear ${user^},
You ordered ${#items[@]} items:
$(for i in "${!items[@]}"; do printf ' %d. %s\n' $((i+1)) "${items[i]}"; done)
Total letters: $total
Date: $(printf '%(%Y)T' 0)
EOT
