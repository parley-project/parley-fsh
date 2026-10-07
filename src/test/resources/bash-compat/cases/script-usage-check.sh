main() { if [ $# -lt 1 ]; then echo "usage: main arg" >&2; return 2; fi; echo "arg=$1"; }; main; echo $?; main hi
