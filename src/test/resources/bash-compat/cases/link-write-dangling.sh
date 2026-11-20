# needs: ln cat
ln -s made.txt dang
[ -e made.txt ] || echo no-target-yet
echo hi > dang
[ -f made.txt ] && echo target-created; cat made.txt; [ -L dang ] && echo still-link
echo more >> dang; cat dang
