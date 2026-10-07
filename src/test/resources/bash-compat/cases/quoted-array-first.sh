f() { echo "$FUNCNAME"; }; f; a=(x y); echo "$a" "${a}" "${a[1]}"
