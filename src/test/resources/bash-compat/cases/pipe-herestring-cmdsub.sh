cat <<< "$(printf '%s\n' x y)" | while read l; do echo "<$l>"; done
