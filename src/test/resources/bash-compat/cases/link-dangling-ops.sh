# needs: ln rm cat
ln -s nothing dang
cat dang 2> /dev/null; echo "st=$?"
[ -L dang ] && echo link; [ -e dang ] || echo missing
rm dang; echo "st=$?"; [ -L dang ] || echo removed
ln -s nothing dang; rm -f dang; [ -L dang ] || echo removed-f
