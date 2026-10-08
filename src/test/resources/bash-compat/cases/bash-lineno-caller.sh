f() {
	echo "f called from ${BASH_LINENO[0]} ${FUNCNAME[1]}"
	g
}
g() {
	echo "g called from ${BASH_LINENO[0]}, f from ${BASH_LINENO[1]}"
	echo "${#BASH_LINENO[@]} ${FUNCNAME[*]}"
}
f
echo "outside: [${BASH_LINENO[*]}]"
