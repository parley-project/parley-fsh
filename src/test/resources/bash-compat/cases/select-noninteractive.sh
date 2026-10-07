echo 2 | { select x in a b c; do echo "$x"; break; done; } 2>/dev/null
