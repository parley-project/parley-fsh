set -u
: "${APP_ENV:=dev}" "${APP_PORT:=8080}"
echo "env=$APP_ENV port=$APP_PORT"
validate() { local v=$1; [[ $v =~ ^[0-9]+$ ]] && (( v > 0 && v < 65536 )); }
validate "$APP_PORT" && echo "port ok"
arr=(); echo "empty array len ${#arr[@]}"
declare -A opts=(); echo "keys ${#opts[@]}"
f() { local x=${1:-none}; echo "arg=$x"; }; f; f y
( echo "$UNDEFINED_VAR" ) 2>/dev/null || echo "unbound caught rc=$?"
echo "${MAYBE-}${MAYBE:+set}" end
