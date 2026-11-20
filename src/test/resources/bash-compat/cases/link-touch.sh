# needs: ln touch ls
touch -t 200101010000 ref; touch -t 200001010000 f; ln -s f lf; touch -h -t 199901010000 lf
echo $(ls -t)
touch -t 200201010000 lf
[ f -nt ref ] && echo target-newer
echo $(ls -t)
touch -h -t 200301010000 lf
[ f -nt ref ] && echo target-still-newer-than-ref
echo $(ls -t)
