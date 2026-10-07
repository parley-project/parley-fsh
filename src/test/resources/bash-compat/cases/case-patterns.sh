for f in a.txt b.sh c.md x; do case $f in *.txt|*.md) echo text;; *.sh) echo script;; *) echo other;; esac; done
