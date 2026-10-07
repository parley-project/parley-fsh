# needs: bc paste sed
calc() { echo "scale=${2:-2}; $1" | bc; }
echo "$(calc '10/3') $(calc '2/3' 4) $(calc 'sqrt(2)' 3)"
c2f() { calc "$1*9/5+32" 1; }
for c in -40 0 37.5 100; do printf '%6s C = %6s F\n' "$c" "$(c2f $c)"; done
avg=$(printf '%s\n' 1.5 2.5 3.25 | paste -sd+ - | sed 's/^/(/; s/$/)\/3/' | bc -l); printf 'avg=%.3f\n' "$avg"
