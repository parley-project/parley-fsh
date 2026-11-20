# needs: ln cat wc
echo one > f; ln -s f lf; ln -s lf lf2
cat lf; cat lf2
echo two >> lf; cat f
echo three > lf2; cat f; n=$(wc -c < lf); echo $((n))
[ -L lf ] && [ -L lf2 ] && echo still-links
read -r x < lf; echo "read:$x"
