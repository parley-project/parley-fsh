chk(){ if "$@"; then echo yes; else echo no; fi; }; chk test -n x; chk [ 1 -lt 2 ]; chk false; c=true; while "$c"; do echo once; c=false; done
