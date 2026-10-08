# without job control a job started with & ignores SIGINT; TERM ends it
work() { while true; do sleep 1; done; }
work &
sleep 0.2
kill -s INT %1
sleep 0.2
jobs
kill %1
wait %1
echo "st=$?"
jobs
