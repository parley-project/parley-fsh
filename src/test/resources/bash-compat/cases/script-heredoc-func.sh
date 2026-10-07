# needs: cat
show() { cat <<EOF
Name: $1
Age: $2
EOF
}; show Ann 30
