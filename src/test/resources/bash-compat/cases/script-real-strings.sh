s="  Hello, World! Hello again.  "
trimmed="${s#"${s%%[![:space:]]*}"}"; trimmed="${trimmed%"${trimmed##*[![:space:]]}"}"
echo "[$trimmed]"
echo "${trimmed//Hello/Bye}" "${#trimmed}"
lower=${trimmed,,}; echo "${lower// /_}"
IFS=' ,.!' read -ra words <<< "$trimmed"
echo "${#words[@]} words: ${words[*]}"
csv=$(IFS=,; echo "${words[*]}"); echo "$csv"
[[ $trimmed =~ ([A-Z][a-z]+),\ ([A-Z][a-z]+) ]] && echo "${BASH_REMATCH[2]} ${BASH_REMATCH[1]}"
printf '%s\n' "${trimmed:7:5}" "${trimmed: -6}"
url="https://user@host.example.com:8443/path/to/file.tar.gz?x=1"
proto=${url%%://*}; rest=${url#*://}; hostport=${rest%%/*}; host=${hostport#*@}; host=${host%%:*}; port=${hostport##*:}
file=${url##*/}; file=${file%%\?*}; ext=${file#*.}
echo "$proto $host $port $file $ext"
