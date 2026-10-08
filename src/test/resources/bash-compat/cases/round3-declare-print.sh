# declare -p: $'..' quoting, declared-but-unset names, attribute filters
x=$'a\nb'; declare -p x
z=( $'q\nr' "s\"t" ); declare -p z
declare -A m=(["\$k"]=1); declare -p m
declare -A t=(["~"]=3); declare -p t
declare -A p=([plain]=2); declare -p p
declare -i n; declare -p n
declare -a b[256]; declare -p b
declare -lr l=ABC; declare -p l
a=abc; declare -a a; declare -p a
declare -a d='([1]="" [5]="x y")'; declare -p d
