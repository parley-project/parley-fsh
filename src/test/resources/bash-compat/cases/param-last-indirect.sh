set -- a b c; echo ${!#} ${@: -1} $#
f(){ echo ${!#}; }; f x y
n=2; echo ${!n}; X=/home/test; v=X; echo ${!v:+set} ${!v#/home/}; w=nope; echo "[${!w-dflt}]"
