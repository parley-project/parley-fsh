for x in a b c d; do case $x in a|b) echo ab;; c) echo c;; *) echo other;; esac; done
