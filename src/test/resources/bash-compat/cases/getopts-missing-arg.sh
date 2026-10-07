f() { OPTIND=1; while getopts ":a:" o; do echo "$o $OPTARG"; done; }; f -a
