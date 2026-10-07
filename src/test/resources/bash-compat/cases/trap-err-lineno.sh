trap 'echo "line $LINENO: $BASH_COMMAND"' ERR
true
false
ls /nonexistent 2>/dev/null
