declare -A s; s=([a]=1 [b]=2); echo ${s[a]} ${s[b]}; s+=([c]=3 [a]=9); echo ${#s[@]} ${s[a]} ${s[c]}; k=x; s=([$k]="v w"); echo ${#s[@]} "${s[x]}"
f(){ local -n o=$1; o=([x]=7 [y]=8); }; declare -A t; f t; echo ${t[x]} ${t[y]}
