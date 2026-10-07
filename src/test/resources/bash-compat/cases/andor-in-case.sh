case x in x) true && echo a;; esac; for p in a "" b; do case $p in "") false || echo empty;; *) [ -n "$p" ] && echo "[$p]";; esac; done
