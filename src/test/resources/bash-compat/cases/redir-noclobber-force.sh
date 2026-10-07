# needs: cat
set -C; echo a > f; echo b > f 2>/dev/null; echo $?; echo c >| f; cat f
