# needs: sort
declare -A deps=([app]="lib util" [lib]="core" [util]="core" [core]="" [test]="app")
declare -A visited; order=()
visit() { local n=$1 d; [[ -n ${visited[$n]:-} ]] && return; visited[$n]=1; for d in ${deps[$n]}; do visit "$d"; done; order+=("$n"); }
for n in $(printf '%s\n' "${!deps[@]}" | sort); do visit "$n"; done
echo "build order: ${order[*]}"
declare -A indeg; for n in "${!deps[@]}"; do indeg[$n]=${indeg[$n]:-0}; for d in ${deps[$n]}; do :; done; done
echo "nodes: ${#deps[@]} roots: $(for n in "${!deps[@]}"; do [[ -z ${deps[$n]} ]] && echo -n "$n "; done)"
