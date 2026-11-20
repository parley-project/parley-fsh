# needs: ln mkdir touch
mkdir dir; touch dir/a dir/b; ln -s dir ld
echo ld/*; ls ld
echo x > ld/c; ls dir
for f in ld/*; do echo "item:$f"; done
cd ld; echo "${PWD##*/}"; pwd -P | { read p; echo "${p##*/}"; }; cd ..
[ -d ld ] && echo is-dir; [ -L ld ] && echo is-link
