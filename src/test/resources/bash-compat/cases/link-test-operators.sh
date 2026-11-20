# needs: ln touch mkdir
touch f; mkdir d; ln -s f lf; ln -s d ld; ln -s lf lf2; ln -s nothing dang
for n in f lf lf2 ld dang; do
  r=""
  [ -L $n ] && r="$r L"; [ -h $n ] && r="$r h"; [ -e $n ] && r="$r e"; [ -f $n ] && r="$r f"; [ -d $n ] && r="$r d"
  echo "$n:$r"
done
