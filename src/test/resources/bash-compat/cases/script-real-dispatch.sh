# needs: sed tr
cmd_add() { echo $(( $1 + $2 )); }
cmd_mul() { echo $(( $1 * $2 )); }
cmd_help() { echo "commands: $(compgen -A function cmd_ | sed 's/cmd_//' | tr '\n' ' ')"; }
run() { local c=$1; shift; if declare -F "cmd_$c" >/dev/null; then "cmd_$c" "$@"; else echo "no such command: $c"; return 1; fi; }
run add 2 3; run mul 4 5; run help; run div 1 2 || echo "rc=$?"
op=mul; eval "cmd_$op 6 7"
action='echo "evaluated $((2*21))"'; eval "$action"
