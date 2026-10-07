f(){ local IFS=_ parts; read -ra parts <<< "$1"; echo "${#parts[@]} ${parts[*]}"; }; f a_b_c; f single; echo "global=${#parts[@]}"
