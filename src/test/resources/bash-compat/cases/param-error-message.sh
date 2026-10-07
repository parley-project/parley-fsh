# needs: sed
( : ${nope:?custom message} ) 2>&1 | sed "s/.*nope: //"
