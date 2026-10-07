f() { echo "$1|$2|$#|$@|$*"; shift; echo "$1|$#"; }; f x y z
