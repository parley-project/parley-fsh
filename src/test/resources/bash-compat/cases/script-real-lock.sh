# needs: mkdir rmdir
lockdir=./my.lock
acquire() { mkdir "$lockdir" 2>/dev/null; }
release() { rmdir "$lockdir"; }
if acquire; then echo "got lock"; else echo "busy"; fi
if acquire; then echo "got lock again?"; else echo "busy second time"; fi
release && echo "released"
acquire && echo "got it back" && release
[ -d "$lockdir" ] || echo "clean"
