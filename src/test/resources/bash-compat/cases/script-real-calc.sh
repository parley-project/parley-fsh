tokens=(); pos=0
tokenize() { local s=$1 t; tokens=(); while [[ -n $s ]]; do
  if [[ $s =~ ^[[:space:]]+(.*)$ ]]; then s=${BASH_REMATCH[1]}
  elif [[ $s =~ ^([0-9]+)(.*)$ ]]; then tokens+=("${BASH_REMATCH[1]}"); s=${BASH_REMATCH[2]}
  else tokens+=("${s:0:1}"); s=${s:1}; fi; done; pos=0; }
peek() { echo "${tokens[pos]:-}"; }
expr_() { local v; v=$(term); while [[ $(peek) == [+-] ]]; do local op=${tokens[pos]}; ((pos++)); local r; r=$(term_at); v=$(( op == "+" ? v + r : v - r )); done; echo $v; }
term() { factor_val; }
term_at() { factor_val; }
factor_val() { local t=${tokens[pos]}; ((pos++)); echo "$t"; }
calc() { tokenize "$1"; local total=${tokens[0]} i=1; while (( i < ${#tokens[@]} )); do local op=${tokens[i]} n=${tokens[i+1]}; case $op in +) total=$((total+n));; -) total=$((total-n));; '*') total=$((total*n));; /) total=$((total/n));; esac; ((i+=2)); done; echo "$1 = $total"; }
calc "1 + 2 + 3"; calc "10 - 4 * 2"; calc "100 / 7"
tokenize "12+(3*4)"; echo "${#tokens[@]} tokens: ${tokens[*]}"
