printf "a\nb" | while IFS= read -r l || [ -n "$l" ]; do echo "<$l>"; done
x=old; read x < /dev/null; echo "[$x] $?"
