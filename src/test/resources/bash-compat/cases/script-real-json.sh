# needs: sort
json_escape() { local s=$1; s=${s//\\/\\\\}; s=${s//\"/\\\"}; s=${s//$'\n'/\\n}; s=${s//$'\t'/\\t}; printf '"%s"' "$s"; }
declare -A user=([name]='Ann "the" Dev' [city]=$'Oslo\tNorway' [id]=7)
out="{"; sep=""
for k in $(printf '%s\n' "${!user[@]}" | sort); do
  v=${user[$k]}; [[ $v =~ ^[0-9]+$ ]] || v=$(json_escape "$v")
  out+="$sep$(json_escape "$k"):$v"; sep=","
done
out+="}"; echo "$out"
items=(a "b c" 'd"e'); arr="["; sep=""; for i in "${items[@]}"; do arr+="$sep$(json_escape "$i")"; sep=","; done; arr+="]"; echo "$arr"
