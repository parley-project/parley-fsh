if x="$(echo hi)"; then echo "ok $x"; fi; if ! y=$(false); then echo "failed"; fi; if z=$(true) &&
   [ -z "$z" ]; then echo both; fi
