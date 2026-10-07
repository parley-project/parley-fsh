state=idle; events=(start pause resume stop start bogus)
for e in "${events[@]}"; do
  case "$state:$e" in
    idle:start) state=running;;
    running:pause) state=paused;;
    paused:resume) state=running;;
    running:stop|paused:stop) state=stopped;;
    stopped:start) state=running;;
    *) echo "ignored $e in $state"; continue;;
  esac
  echo "$e -> $state"
done
