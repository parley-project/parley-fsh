set -T
trap 'echo "D[$BASH_COMMAND]"' DEBUG
f() { echo in f; }
f
(echo sub)
trap - DEBUG
