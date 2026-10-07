wrap() { local width=$1 line="" w; shift; for w in $*; do if (( ${#line} + ${#w} + 1 > width )) && [[ -n $line ]]; then echo "$line"; line=$w; else line=${line:+$line }$w; fi; done; [[ -n $line ]] && echo "$line"; }
wrap 20 The quick brown fox jumps over the lazy dog and keeps running far away
echo ---
wrap 10 supercalifragilistic is long
