f() { echo out; echo err >&2; } > o.txt 2>&1; f; cat o.txt
g() ( echo sub ) >> o.txt; g; cat o.txt
