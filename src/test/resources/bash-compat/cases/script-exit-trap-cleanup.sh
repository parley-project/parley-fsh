# needs: rm touch
tmp=work.tmp; cleanup() { rm -f "$tmp"; echo cleaned; }; trap cleanup EXIT; touch "$tmp"; [ -f "$tmp" ] && echo created
