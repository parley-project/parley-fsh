coproc { read l; echo "got $l"; }
echo "${COPROC[@]} pid:${COPROC_PID:+set}"
echo hi >&${COPROC[1]}
read r <&${COPROC[0]}; echo "$r"
wait $COPROC_PID; echo st=$?
coproc NAMED { echo named; }
read x <&${NAMED[0]}; echo "$x ${NAMED_PID:+pid}"
coproc cat
echo hello >&${COPROC[1]}
exec {COPROC[1]}>&-
read y <&${COPROC[0]}; echo "$y"
