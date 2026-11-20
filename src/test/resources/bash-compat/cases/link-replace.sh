# needs: ln mkdir readlink
mkdir d1 d2; echo a > f; echo b > g
ln -s f l; ln -s g l 2> /dev/null; echo "st=$?"; readlink l
ln -sf g l; readlink l
ln -s d1 ld; ln -sfn d2 ld; readlink ld; [ -e d1/d2 ] || echo not-inside-d1; [ -L d2/d2 ] || echo not-inside-d2
