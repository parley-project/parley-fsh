mapfile -t lines < <(printf 'a\nb\n'); echo ${#lines[@]} ${lines[1]}
