# A run of fsh's common work, for the JDK's AOT cache (see macos/fsh): what it loads and how it
# runs here is what the cache keeps, so fsh starts and warms up faster.
x=1 s='a b c' arr=(one two three)
declare -A map=([k1]=v1 [k2]=v2)
f() { local -i n=$1; local v="${2:-dflt}"; [[ $v == d* && n -gt 0 ]] && return 0; return 1; }
for ((i=0; i<300; i++)); do
	y=${x}$i; z=$(( i * 2 + 1 )); arr+=("$y")
	f "$i" "$y" || true
	case $y in 1*) : ;; *[0-9]) : ;; *) : ;; esac
	[ "$i" -gt 5 ] && t="${s// /_}" || t="${s#a }"
	printf -v p '%s-%03d' "$t" "$i"
	eval "e=\"\${arr[$((i % 3))]}\""
done
for w in $s "${arr[@]:0:3}" "${!map[@]}"; do v=${w^^}; v=${v,,}; v=${#v}; done
words=$(echo "$s" | tr ' ' '\n' | sort -r); set -- $words; shift
while IFS= read -r line; do n=${line%%:*}; done <<'EOF'
a:1
b:2
EOF
trap 'trapped=1' USR1; trap - USR1
out=$( { echo one; echo two >&2; } 2>&1 ); r=$(/usr/bin/true; echo $?)
cd / && cd - > /dev/null
test -d / && [[ -n $out && $out =~ ^o ]] && { echo ok; } > /dev/null
( sub=1; echo "$sub" ) > /dev/null
type f > /dev/null; command -v true > /dev/null; read -r a b <<< "1 2"
