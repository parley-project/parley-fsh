while IFS="=" read -r k v; do [[ -z $k || $k == \#* ]] && continue; echo "key=$k val=$v"; done <<EOF
# comment
a=1
b=two words

c=3
EOF
