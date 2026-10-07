declare -A m=([k]=v); [[ -v m[k] ]] && echo has; [[ -v m[z] ]] || echo hasnot
