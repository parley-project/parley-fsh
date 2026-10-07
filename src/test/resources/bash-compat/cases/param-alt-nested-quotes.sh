x=a; echo "${x:+"b c"}" ${y:-"d e"}; for w in ${y:-"1 2" 3}; do echo "[$w]"; done
