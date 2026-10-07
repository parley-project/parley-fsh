[[ -o errexit ]] || echo noerrexit; set -e; [[ -o errexit ]] && echo errexit; [[ -o nosuchopt ]]; echo $?
