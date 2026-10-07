box() { local text=$1 w=$(( ${#1} + 4 )) line; printf -v line '%*s' $w ''; line=${line// /-}; echo "+$line+"; printf '|  %s  |\n' "$text"; echo "+$line+"; }
box "Hello"; box "fsh compat"
center() { local w=$1 s=$2 pad=$(( (w - ${#2}) / 2 )); printf '%*s%s%*s|\n' $pad '' "$s" $((w - pad - ${#s})) ''; }
center 20 abc; center 20 "longer text"
printf '%s\n' "$(printf '=%.0s' {1..15})"
