printf "a\nb\nc\n" > f
exec 3<f; read -u 3 x; read -u 3 y; echo $x $y
exec {fd}<f; read -u $fd z; echo $z
exec 3<&- {fd}<&-
while read -r -u 4 line; do echo "<$line>"; done 4<f
