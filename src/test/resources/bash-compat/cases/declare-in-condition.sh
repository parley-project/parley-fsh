f(){ :; }; if declare -F f >/dev/null; then echo has-f; fi; if ! declare -F nope >/dev/null; then echo no-nope; fi; c=f; declare -F "$c"
