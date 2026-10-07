# needs: cat
cat > app.conf <<'CONF'
# comment
name = My App
port=8080
  debug = true
empty =
path = /opt/app # trailing
CONF
declare -A cfg
while IFS='=' read -r key value; do
  key=${key%%#*}; key=${key//[[:space:]]/}
  [[ -z $key ]] && continue
  value=${value%%#*}
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  cfg[$key]=$value
done < app.conf
for k in $(printf '%s\n' "${!cfg[@]}" | sort); do printf '%s=[%s]\n' "$k" "${cfg[$k]}"; done
