f() { echo out; echo err >&2; }; f 2>&1 | while read l; do echo "[$l]"; done
