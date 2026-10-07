slugify() { local s=${1,,}; s=${s//[^a-z0-9]/-}; while [[ $s == *--* ]]; do s=${s//--/-}; done; s=${s#-}; s=${s%-}; echo "$s"; }
title() { local w out=(); for w in $1; do out+=("${w^}"); done; echo "${out[*]}"; }
for t in "Hello, World!" "  Bash  Scripting 101 " "Ünïcode & Symbols??"; do echo "[$(slugify "$t")] [$(title "${t,,}")]"; done
camel() { local IFS=_ parts; read -ra parts <<< "$1"; local r=${parts[0]} p; for p in "${parts[@]:1}"; do r+=${p^}; done; echo "$r"; }
camel snake_case_name; camel single
