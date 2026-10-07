typeset -i n=2+3; typeset s=x; echo $n $s; f(){ typeset l=loc; echo $l; }; f; typeset -f f | head -1
