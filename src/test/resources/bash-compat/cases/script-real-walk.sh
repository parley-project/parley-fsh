# needs: mkdir touch
mkdir -p t/a/b t/c; touch t/1.txt t/a/2.txt t/a/b/3.log t/c/4.txt
walk() { local d=$1 depth=$2 f; for f in "$d"/*; do [ -e "$f" ] || continue; printf '%*s%s%s\n' $((depth*2)) '' "${f##*/}" "$([ -d "$f" ] && echo /)"; [ -d "$f" ] && walk "$f" $((depth+1)); done; }
walk t 0
count() { local n=0 f; for f in "$1"/* ; do if [ -d "$f" ]; then n=$((n + $(count "$f"))); elif [[ $f == *.txt ]]; then n=$((n+1)); fi; done; echo $n; }
echo "txt files: $(count t)"
