# needs: grep
echo $$ | grep -q "^[0-9][0-9]*$" && echo pid
