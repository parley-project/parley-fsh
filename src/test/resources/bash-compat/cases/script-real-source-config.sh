# needs: cat sort
cat > gen.conf <<'CONF'
# generated
NAME="My App"
PORT=9090
FEATURES=(auth logging "rate limit")
declare -A LIMITS=([read]=100 [write]=10)
CONF
. ./gen.conf
echo "$NAME on $PORT with ${#FEATURES[@]} features: ${FEATURES[2]}"
for k in $(printf '%s\n' "${!LIMITS[@]}" | sort); do echo "$k=${LIMITS[$k]}"; done
( PORT=1; . ./gen.conf; echo "reloaded $PORT" ); echo "still $PORT"
