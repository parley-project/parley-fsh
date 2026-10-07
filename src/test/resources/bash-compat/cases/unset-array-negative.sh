a=(1 2 3); unset "a[-1]"; echo ${a[@]} ${#a[@]}; b=(x y z); unset "b[-3]"; echo ${b[@]} ${!b[@]}
