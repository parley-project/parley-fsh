printf "a\nb\nc\n" | { mapfile arr; echo ${#arr[@]} "[${arr[0]%$'\n'}]"; }
