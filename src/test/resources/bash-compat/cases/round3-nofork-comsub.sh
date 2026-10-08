# ${ list; }: return and local, caller's parameters
echo ${ printf '%s\n' aa bb; return; echo cc; }
unset x
echo ${ local x; x=42; echo in $x; }
echo "out [$x]"
set -- 1 2
: "${ shift; }"
echo "$@"
a=${| REPLY=42; }
echo $a
