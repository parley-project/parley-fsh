# needs: cat wc tr
name=X
cat <<EOF1
plain $name $(echo cmd) \$escaped
EOF1
cat <<'EOF2'
literal $name $(echo no)
EOF2
cat <<-EOF3
	tabs stripped $name
	EOF3
while read -r a b; do echo "$b-$a"; done <<EOF4
1 one
2 two
EOF4
sql=$(cat <<EOF5
SELECT *
FROM t WHERE n = '$name';
EOF5
); echo "$sql" | wc -l | tr -d ' '
