n=0; until (( n >= 3 )); do (( n++ )); [ $n -eq 2 ] && { echo "ok at $n"; break; }; done
