# needs: touch
touch a.c b.c; f=(*.c); echo ${#f[@]} ${f[1]}
