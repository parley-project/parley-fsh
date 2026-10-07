{ echo out; echo err >&2; } 3>&1 1>&2 2>&3 2>/dev/null
