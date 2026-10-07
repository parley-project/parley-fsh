x=$(case a in a) echo yes;; esac); echo $x
y=$(case b in (a) echo A;; (b) echo B;; esac); echo $y
