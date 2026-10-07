declare -n r=x; x=1; unset -n r; echo "[$r] $x"; declare -n q=x; unset q; echo "[${x-unset}]"
