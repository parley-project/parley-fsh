printf "a;b;c" | { read -d ";" x; echo $x; }
