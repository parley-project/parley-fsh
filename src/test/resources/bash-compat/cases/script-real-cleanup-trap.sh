# needs: mktemp rm
tmp=$(mktemp -d)
cleanup() { local rc=$?; rm -rf "$tmp"; echo "cleanup rc=$rc exists=$([ -d "$tmp" ] && echo y || echo n)"; }
trap cleanup EXIT
echo data > "$tmp/f"
[ -s "$tmp/f" ] && echo "wrote file"
false || echo "recovered"
exit 3
