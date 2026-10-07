echo | { set -u; case $- in *u*) echo inside-u;; esac; }; case $- in *u*) echo leaked;; *) echo outside-no-u;; esac
echo | { shopt -s nullglob; for f in *.zzz; do echo "$f"; done; }; for f in *.zzz; do echo "$f"; done
