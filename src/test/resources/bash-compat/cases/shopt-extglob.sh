# needs: touch
shopt -s extglob
touch a.c b.h c.o
echo !(*.o)
