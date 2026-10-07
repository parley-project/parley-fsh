# needs: mkdir touch
mkdir d; touch f; [ -d d ] && echo dir; [ -f f ] && echo file; [ -e nope ] || echo missing; [ -f d ] || echo notfile
