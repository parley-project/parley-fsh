# needs: sort tr
declare -A m=([b]=2 [a]=1); for k in $(echo ${!m[@]} | tr " " "\n" | sort); do echo "$k=${m[$k]}"; done
