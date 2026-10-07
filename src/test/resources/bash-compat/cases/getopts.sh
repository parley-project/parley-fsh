f() { while getopts "ab:" o; do echo $o $OPTARG; done; }; f -a -b val
