# ${x:?} and set -u end a script
echo start
echo ${nope:?is not set}; echo not-run
echo not-run-either
