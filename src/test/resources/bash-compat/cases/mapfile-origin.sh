printf "a\nb\nc\nd\n" > f; mapfile -t -O 5 y < f; echo ${!y[@]} ${y[5]}; mapfile -t -n 2 -s 1 x < f; echo "${x[@]}"
