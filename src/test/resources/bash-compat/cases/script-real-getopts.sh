run() {
  local OPTIND opt verbose=0 out=- level=1
  while getopts ":vo:l:h" opt; do
    case $opt in
      v) ((verbose++));;
      o) out=$OPTARG;;
      l) [[ $OPTARG =~ ^[0-9]+$ ]] || { echo "bad level" >&2; return 2; }; level=$OPTARG;;
      h) echo "help"; return 0;;
      :) echo "missing arg for -$OPTARG"; return 2;;
      \?) echo "unknown -$OPTARG"; return 2;;
    esac
  done
  shift $((OPTIND-1))
  echo "v=$verbose out=$out level=$level args=$*"
}
run -vv -o file -l 3 a b; run -x; run -o; run -h; run -l x 2>&1; echo rc=$?
