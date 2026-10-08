set -o posix; echo "posix=$?"; set +o posix
set -o | grep -E '^(hashall|keyword|monitor|posix|privileged|emacs|vi|nolog|ignoreeof) '
echo "flags=$-"
set +h; echo "flags=$-"; set -h
set -k; f() { echo "c=$c d=$d args=$*"; }; f a c=7 b d=8; set +k; f a c=7
shopt -po pipefail
shopt -os pipefail; set -o | grep pipefail; shopt -ou pipefail
shopt cmdhist extdebug extquote nosuch; echo "st=$?"
shopt -q cmdhist; echo "q=$?"
x=1; y='a b'; set | grep -E '^(x|y)='
