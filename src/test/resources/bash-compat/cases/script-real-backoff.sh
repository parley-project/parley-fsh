attempt=0
unreliable() { ((attempt++)); ((attempt >= 4)); }
backoff() { local max=$1 delay=1 i; shift; for ((i=1;i<=max;i++)); do if "$@"; then echo "ok after $i"; return 0; fi; echo "attempt $i failed, waiting ${delay}s"; delay=$((delay*2)); done; echo "giving up"; return 1; }
backoff 5 unreliable; attempt=0; backoff 2 unreliable || echo "rc=$?"
