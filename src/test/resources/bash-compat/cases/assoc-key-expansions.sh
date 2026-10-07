declare -A m; s=a; k=b; m[$s.$k]=v; m[$s-$k]=w; echo "${m[a.b]} ${m[a-b]} ${m[$s.$k]}"; a=(10 20 30); i=1; echo ${a[i+1]} ${a[$i]}
