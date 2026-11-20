# needs: ln cp mv rm mkdir
echo data > f; ln -s f lf
cp lf copy; [ -L copy ] || echo copy-is-file; cat copy
cp -P lf plink; [ -L plink ] && echo P-is-link; readlink plink
mv lf moved; [ -L moved ] && echo moved-is-link; [ -e lf ] || echo lf-gone; cat f
ln -s f lf; rm lf; [ -e f ] && echo target-kept; [ -L lf ] || echo link-gone
mkdir d; echo x > d/a; ln -s a d/la; cp -R d d2; [ -L d2/la ] && echo R-kept-link
