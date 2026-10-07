# needs: cat
cat > app.ini <<'INI'
[server]
host = example.com
port = 8080

[database]
; comment
user = admin
pass = s3cr=t
INI
declare -A ini; section=
while IFS= read -r line || [[ -n $line ]]; do
  line=${line%%;*}
  [[ $line =~ ^[[:space:]]*$ ]] && continue
  if [[ $line =~ ^\[(.+)\]$ ]]; then section=${BASH_REMATCH[1]}; continue; fi
  key=${line%%=*}; val=${line#*=}
  key=$(echo $key); val=$(echo $val)
  ini[$section.$key]=$val
done < app.ini
for k in $(printf '%s\n' "${!ini[@]}" | sort); do echo "$k=${ini[$k]}"; done
echo "port+1=$(( ${ini[server.port]} + 1 ))"
