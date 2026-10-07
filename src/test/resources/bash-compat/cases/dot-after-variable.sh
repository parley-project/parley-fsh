s=a; f=name; echo $s.x "$f.txt" "${f}.txt"; echo "$s.$f"; declare -A m; m[$s.$f]=v; echo "${m[a.name]}"; cp_name="$f.bak"; echo "$cp_name"
