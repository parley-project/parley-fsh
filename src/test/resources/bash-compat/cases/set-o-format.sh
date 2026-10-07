# needs: grep
set -o pipefail; set -o | grep -E "^(errexit|noglob|pipefail) "; set +o | grep -E " (errexit|pipefail)$"
