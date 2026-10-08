# an arithmetic error, a bad substitution or a readonly variable abandons the rest of the line;
# the script goes on with the next one
echo $(( 2#44 )); echo not-run-1
echo "after 1 $?"
x=$(( 4 / 0 )); echo not-run-2
echo "after 2 x=$x"
echo ${x!@#}; echo not-run-3
echo after 3
readonly ro=1
ro=2; echo not-run-4
echo after 4
f() { ro=3; echo not-run-5; }; f; echo not-run-6
echo after 5
ro=4 echo runs-anyway
let 'a = 7 + (3'; echo "let fails $?"
( echo $((1+)); echo not-run-7 ); echo "subshell $?"
y=$(echo $((1+)); echo not-run-8); echo "comsub [$y]"
echo end
