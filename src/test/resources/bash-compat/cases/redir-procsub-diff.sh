# needs: diff
diff <(echo a) <(echo b) > /dev/null; echo $?
