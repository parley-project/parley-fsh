# needs: mkdir find ls head wc tr
mkdir -p proj/src proj/docs
for f in a.c b.c c.h; do echo "// $f" > proj/src/$f; done
echo "# readme" > proj/docs/README.md
shopt -s nullglob globstar
count=0; for f in proj/**/*.c; do count=$((count+1)); done; echo "c files: $count"
for f in proj/src/*.{c,h}; do printf '%s %s\n' "${f##*/}" "$(wc -l < "$f" | tr -d ' ')"; done
missing=(proj/*.none); echo "missing: ${#missing[@]}"
while IFS= read -r -d '' f; do echo "found ${f#proj/}"; done < <(find proj -name '*.md' -print0)
[[ -d proj/src && ! -e proj/bin ]] && echo "layout ok"
newest=$(ls -t proj/src | head -1); [[ -n $newest ]] && echo "newest set"
