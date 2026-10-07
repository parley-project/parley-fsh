# needs: sed
( set -x; echo hi ) 2>&1 | sed "s/^+* //"
