f() { local -a arr=(1 2); local -i n=2+3; local -r ro=1; local x y=2; echo ${arr[1]} $n $ro "[$x]" $y; }; f
