f() { echo out; echo err >&2; }; x=$(f 2>/dev/null); echo "[$x]"
