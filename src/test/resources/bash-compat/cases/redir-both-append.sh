# needs: cat
f() { echo o; echo e >&2; }; f &>> log; f &>> log; cat log
