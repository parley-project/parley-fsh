# needs: cat
work() { sleep "0.$1"; echo "job $1 done"; return $(( $1 % 2 )); }
pids=()
for i in 3 1 2; do work $i > "out.$i" & pids+=($!); done
fail=0
for p in "${pids[@]}"; do wait "$p" || ((fail++)); done
cat out.1 out.2 out.3
echo "failures=$fail jobs=${#pids[@]}"
