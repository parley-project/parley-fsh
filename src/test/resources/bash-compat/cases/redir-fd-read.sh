echo l1 > f; exec 4< f; read x <&4; exec 4<&-; echo $x
