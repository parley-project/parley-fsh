# needs: head
counter=0
inc() { local by=${1:-1}; counter=$((counter + by)); }
inc; inc 5; echo "counter=$counter"
fact() { local n=$1; (( n <= 1 )) && { echo 1; return; }; echo $(( n * $(fact $((n-1))) )); }
echo "5! = $(fact 5)"
greet() { local -n out=$1; out="hello $2"; }
greet msg "bob"; echo "$msg"
apply() { local fn=$1; shift; local x; for x; do "$fn" "$x"; done; }
shout() { echo "${1^^}!"; }
apply shout one two
outer() { local v=outer; inner; }; inner() { echo "inner sees $v"; }
v=global; outer; echo "after $v"
typeset -f shout | head -1
