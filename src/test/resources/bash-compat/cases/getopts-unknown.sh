f() { OPTIND=1; while getopts "a" o 2>/dev/null; do echo "$o"; done; }; f -z -a
