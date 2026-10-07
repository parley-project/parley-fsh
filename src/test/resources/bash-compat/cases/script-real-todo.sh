# needs: wc mktemp mv grep
db=todo.txt; : > "$db"
add() { local id=$(( $(wc -l < "$db") + 1 )); printf '%d|open|%s\n' "$id" "$*" >> "$db"; echo "added #$id"; }
done_() { local tmp=$(mktemp); while IFS='|' read -r id st text; do [[ $id == "$1" ]] && st=done; printf '%s|%s|%s\n' "$id" "$st" "$text"; done < "$db" > "$tmp"; mv "$tmp" "$db"; }
list() { while IFS='|' read -r id st text; do printf '%2d [%s] %s\n' "$id" "$([[ $st == done ]] && echo x || echo ' ')" "$text"; done < "$db"; }
add buy milk; add "write report"; add call "Ann's" office; done_ 2; list
echo "open: $(grep -c '|open|' "$db")"
