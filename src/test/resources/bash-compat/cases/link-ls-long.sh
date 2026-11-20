# needs: ln mkdir ls
echo hello > f; mkdir d; ln -s f lf; ln -s d ld; ln -s nothing dang
for n in f lf ld dang; do ls -l $n | { read perm links owner group size rest; echo "$n ${perm:0:1} $size"; }; done
ls -lL lf | { read perm links owner group size rest; echo "L ${perm:0:1} $size"; }
ls -ld ld/ | { read perm links owner group size rest; echo "slash ${perm:0:1}"; }
ls -l lf | { read perm links owner group size mon day time name arrow target; echo "$name $arrow $target"; }
