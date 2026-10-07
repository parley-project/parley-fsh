set -- a b c d; echo "${@:2:2}" "${*: -1}"; for w in "${@:2}"; do echo "[$w]"; done
