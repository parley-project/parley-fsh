# needs: od
export LC_ALL=en_US.UTF-8
a=$'\303\251'; echo "$a ${#a}"
b=$'A\303\251B'; echo "$b ${b: -1} ${b:1:1}"
printf '%b\n' 'A\303\251B'; printf 'x\303\251y\n'; echo -e 'e\xc3\xa9e'
x='абвгдежзиклмноп'; echo "-$x- ${#x} -${x:0:5}-"
read y <<< "$x"; echo "${#y}"
read -n 5 z <<< "$x"; echo "$z ${#z}"
printf 'é\n' | { read w; echo "[$w] ${#w}"; }
printf '%s' "$a" | od -An -tx1
