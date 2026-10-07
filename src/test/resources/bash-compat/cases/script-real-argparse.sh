set -- -v -v -n 3 --name="big bear" a "b c" -- -d e
set -euo pipefail
usage() { echo "usage: ${0##*/} [-v] [-n NUM] [--name=NAME] [--] files..." >&2; exit 2; }
verbose=0; num=1; name=world; files=()
while (( $# )); do
  case $1 in
    -v|--verbose) verbose=$((verbose+1)) ;;
    -n) shift; num=${1:?missing}; ;;
    -n*) num=${1#-n} ;;
    --name=*) name=${1#*=} ;;
    --) shift; files+=("$@"); break ;;
    -*) usage ;;
    *) files+=("$1") ;;
  esac
  shift
done
printf 'verbose=%d num=%d name=%s files=%d\n' "$verbose" "$num" "$name" "${#files[@]}"
for f in "${files[@]}"; do printf '  [%s]\n' "$f"; done
