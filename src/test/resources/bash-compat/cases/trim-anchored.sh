x=/a/b.tar.gz; echo ${x%/} ${x#a} ${x%.*} ${x%%.*} ${x#*/} ${x##*/} ${x%g?}; y=aXbXc; echo ${y%X*} ${y%%X*} ${y#*X} ${y##*X}; echo "[${u//x/V}][${u#a}][${u^^}]"
