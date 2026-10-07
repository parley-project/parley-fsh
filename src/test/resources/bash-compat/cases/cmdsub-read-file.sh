printf "line1\nline2\n" > in.txt; x=$(< in.txt); echo "$x"; echo "[$(<in.txt)]"
