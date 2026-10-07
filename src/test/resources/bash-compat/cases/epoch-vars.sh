a=$EPOCHSECONDS; [ "$a" -gt 1600000000 ] && echo secs
[[ $EPOCHREALTIME =~ ^[0-9]+\.[0-9]{6}$ ]] && echo real
b=${EPOCHREALTIME%.*}; [ $((b - a)) -le 1 ] && echo close
