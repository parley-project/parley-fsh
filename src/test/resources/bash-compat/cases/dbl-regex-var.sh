re="^[0-9]+$"; [[ 123 =~ $re ]] && echo num; [[ 12a =~ $re ]] || echo notnum
