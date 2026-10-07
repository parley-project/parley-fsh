f() { echo o; echo e >&2; }; f &> all; while read l; do echo $l; done < all
