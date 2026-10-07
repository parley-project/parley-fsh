shopt -s extglob
for x in abc aabbcc xyz ab; do case $x in +(a)+(b)*(c)) echo "$x yes";; *) echo "$x no";; esac; done
