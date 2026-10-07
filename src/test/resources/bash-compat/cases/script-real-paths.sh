normalize() { local p=$1 out=() part IFS=/; for part in $p; do case $part in ''|.) ;; ..) ((${#out[@]})) && unset 'out[-1]';; *) out+=("$part");; esac; done; local r="/${out[*]}"; echo "${r}"; }
normalize /a/b/../c/./d//e/
normalize /../x
normalize /a/b/c/../../..
mybasename() { local p=${1%/}; echo "${p##*/}"; }
mydirname() { local p=${1%/}; [[ $p == */* ]] && echo "${p%/*}" || echo .; }
for p in /usr/local/bin/ file.txt /a/b.tar.gz rel/dir/x; do echo "$(mydirname "$p") | $(mybasename "$p")"; done
ext() { local b=$(mybasename "$1"); [[ $b == *.* ]] && echo "${b##*.}" || echo none; }; ext a/b.tar.gz; ext Makefile
