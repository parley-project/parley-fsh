set -e; set -o pipefail; f() { false | true; }; if f; then echo no; else echo caught; fi
