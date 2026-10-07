# needs: tee sort tr diff grep paste cat wc seq
printf 'b\na\nc\n' | tee copy.txt | sort > sorted.txt
echo "copy: $(tr '\n' , < copy.txt) sorted: $(tr '\n' , < sorted.txt)"
diff <(printf 'a\nb\n') <(printf 'a\nc\n') | grep -c '^[<>]'
paste -d: <(printf '1\n2\n') <(printf 'x\ny\n')
exec 3> fd3.txt; echo "to three" >&3; exec 3>&-; cat fd3.txt
{ echo out; echo err >&2; } 2> err.txt > out.txt; echo "$(cat out.txt)/$(cat err.txt)"
wc -l < <(seq 5) | tr -d ' '
