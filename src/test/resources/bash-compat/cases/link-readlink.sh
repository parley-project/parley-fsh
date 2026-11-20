# needs: ln readlink
echo x > f; ln -s f lf; ln -s lf lf2; ln -s /no/such/place abs
readlink lf; readlink lf2; readlink abs
readlink f; echo "st=$?"
r=$(readlink -f lf2); echo "${r##*/}"
