PS3="Pick: "
select opt in "List files" "Show date" Quit; do
  case $REPLY in
    1) echo "listing";;
    2) echo "dating";;
    3) echo "bye"; break;;
    *) echo "invalid: $REPLY";;
  esac
done 2>/dev/null <<< $'1\n9\n2\n3'
echo "done with $opt"
