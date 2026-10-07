set -- a b c; IFS=; echo "$*"; f(){ echo $#; }; f $*; f "$*"
