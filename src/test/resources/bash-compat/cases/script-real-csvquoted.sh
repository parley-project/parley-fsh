parse_csv_line() { local line=$1 field="" inq=0 c i; fields=(); for ((i=0;i<${#line};i++)); do c=${line:i:1}
  if ((inq)); then if [[ $c == '"' ]]; then if [[ ${line:i+1:1} == '"' ]]; then field+='"'; ((i++)); else inq=0; fi; else field+=$c; fi
  else case $c in '"') inq=1;; ,) fields+=("$field"); field="";; *) field+=$c;; esac; fi; done; fields+=("$field"); }
while IFS= read -r line; do parse_csv_line "$line"; printf '%d:' "${#fields[@]}"; printf ' [%s]' "${fields[@]}"; echo; done <<'CSV'
name,quote,n
"Smith, John","He said ""hi""",3
plain,,5
CSV
