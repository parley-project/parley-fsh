# needs: /tmp grep
cd /; cd /tmp; [ ~+ = "$PWD" ] && echo plus; [ ~- = / ] && echo minus; echo ~+/x | grep -c /x
