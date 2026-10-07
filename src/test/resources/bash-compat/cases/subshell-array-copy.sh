declare -A m=([a]=1); a=(1 2); x=$( m[b]=2; a[5]=9; echo in ); echo "${#m[@]} ${#a[@]}"; ( m[c]=3; a+=(x) ); echo "${#m[@]} ${#a[@]}"; f(){ local -a l=(1); ( l[3]=x ); echo ${#l[@]}; }; f
