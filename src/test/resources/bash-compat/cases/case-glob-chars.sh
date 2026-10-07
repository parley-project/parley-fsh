for x in a.txt B1 _x; do case $x in *.txt) echo txt;; [A-Z][0-9]) echo code;; _*) echo under;; esac; done
