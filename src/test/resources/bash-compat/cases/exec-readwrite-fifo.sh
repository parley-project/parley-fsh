# needs: mkfifo rm
mkfifo a.pipe
exec 9<> a.pipe
rm -f a.pipe
echo hello >&9
read -u 9 x; echo "x=$x"
echo again >&9
read -t 1 -u 9 y; echo "y=$y st=$?"
read -t 0.2 -u 9 z; echo "z=$z st=$?"
