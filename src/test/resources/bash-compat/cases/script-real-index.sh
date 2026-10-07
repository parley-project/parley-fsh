# needs: tr sed sort uniq awk
declare -A idx
docs=("the cat sat" "the dog ran" "a cat ran fast")
for i in "${!docs[@]}"; do for w in ${docs[i]}; do idx[$w]+="$i "; done; done
for w in cat ran the fast; do echo "$w: ${idx[$w]% }"; done
query() { local w r=""; for w; do r+="${idx[$w]:-} "; done; tr ' ' '\n' <<< "$r" | sed '/^$/d' | sort | uniq -c | awk -v n=$# '$1==n{print $2}' | tr '\n' ' '; echo; }
echo "cat AND ran: $(query cat ran)"
