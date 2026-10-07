printf "a\\tb\n" | { read x; echo "$x"; }; printf "a\\tb\n" | { read -r x; echo "$x"; }
