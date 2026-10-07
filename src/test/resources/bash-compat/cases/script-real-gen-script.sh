# needs: cat chmod bash
cat > gen.sh <<'G'
echo "generated got $# args: $*"
exit 5
G
chmod +x gen.sh
./gen.sh a "b c"; echo "rc=$?"
bash gen.sh x; echo "rc=$?"
out=$(./gen.sh z); echo "[$out]"
