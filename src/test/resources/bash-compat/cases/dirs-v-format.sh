# needs: mkdir sed
mkdir -p a b; T=$PWD; pushd a >/dev/null; pushd ../b >/dev/null; dirs -v | sed "s|$T|T|g"; dirs -p | sed "s|$T|T|g"; dirs +1 | sed "s|$T|T|g"
