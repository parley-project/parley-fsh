# needs: mkdir
T=$PWD; mkdir -p a b d/sub; cd a; cd ../b; cd - > $T/o; basename "$(cat $T/o)"; basename "$PWD"
CDPATH=$T/d; cd sub > $T/o2; basename "$(cat $T/o2)"; basename "$PWD"; cd /nonexistent-dir 2>/dev/null; echo $?
