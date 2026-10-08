# needs: cat
exec 6>&1
exec >out.txt
echo into-file
exec >&6
cat out.txt
