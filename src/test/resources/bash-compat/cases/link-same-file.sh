# needs: ln
echo x > f; echo y > g; ln -s f l1; ln -s f l2; ln -s l1 l3
[ l1 -ef f ] && echo l1-f; [ l1 -ef l2 ] && echo l1-l2; [ l3 -ef f ] && echo l3-f; [ l1 -ef g ] || echo l1-not-g
