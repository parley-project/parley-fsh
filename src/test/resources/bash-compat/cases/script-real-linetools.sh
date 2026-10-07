# needs: tr grep cut paste
printf 'alpha\nbeta\n\ngamma\ndelta\n' > in.txt
n=0; while IFS= read -r l; do ((n++)); [[ -z $l ]] && continue; printf '%4d  %s\n' $n "$l"; done < in.txt
mapfile -t L < in.txt; for ((i=${#L[@]}-1; i>=0; i--)); do echo "${L[i]}"; done | tr '\n' ','; echo
longest=; for l in "${L[@]}"; do (( ${#l} > ${#longest} )) && longest=$l; done; echo "longest=$longest"
grep -n a in.txt | cut -d: -f1 | paste -s -d, -
