# needs: ln cat
ln -s a b; ln -s b a
[ -e a ] || echo not-exists; [ -L a ] && echo is-link; [ -f a ] || echo not-file
cat a 2> /dev/null; echo "st=$?"
echo x > a 2> /dev/null; echo "st=$?"
