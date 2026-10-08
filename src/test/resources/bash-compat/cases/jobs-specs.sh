# job specs, the job table and its + and - marks, in a script
sleep 2 & sleep 3 &
jobs %1; jobs %-; jobs %+; jobs %?3; echo "st=$?"
jobs %sl; echo "ambiguous=$?"
jobs %9; echo "none=$?"
kill %2; wait %2; echo "st=$?"
jobs
jobs -r; jobs -s; echo "--"
fg; echo "fg=$?"; bg; echo "bg=$?"
disown %1; echo "n=$(jobs | wc -l | tr -d ' ')"
(exit 3) & wait $!; echo "st=$?"
sleep 0.1 & p=$!; wait; wait $p; echo "st=$?"
wait %5; echo "st=$?"
sleep 0.1 & sleep 0.3; jobs; jobs
