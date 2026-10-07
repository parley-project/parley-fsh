f(){ echo hi; local x=1; }; g() { :; }; declare -f f; declare -F; type f; declare -f g
