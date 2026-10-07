# needs: bc
echo $(( 17 / 5 )) $(( 17 % 5 )) $(( -17 / 5 )) $(( 2 ** 10 )) $(( (3+4)*2 ))
printf '%.2f\n' "$(echo 'scale=4; 22/7' | bc)"
x=10; (( x += 5, x *= 2 )); echo $x
hex=$(printf '%x' 255); dec=$(( 16#$hex )); echo "$hex $dec $(( 0x$hex ))"
max() { local m=$1 n; for n; do (( n > m )) && m=$n; done; echo $m; }; max 3 9 4
is_int() { [[ $1 =~ ^-?[0-9]+$ ]]; }; is_int -42 && echo int; is_int 4.2 || echo notint
