a=(apple banana cherry apple date banana)
declare -A seen; uniq=(); for x in "${a[@]}"; do [[ -n ${seen[$x]:-} ]] && continue; seen[$x]=1; uniq+=("$x"); done
echo "uniq: ${uniq[*]}"
b=(banana date fig)
declare -A inb; for x in "${b[@]}"; do inb[$x]=1; done
inter=(); diff=(); for x in "${uniq[@]}"; do if [[ ${inb[$x]:-} ]]; then inter+=("$x"); else diff+=("$x"); fi; done
echo "intersect: ${inter[*]}"; echo "a-b: ${diff[*]}"
union=("${uniq[@]}"); for x in "${b[@]}"; do [[ ${seen[$x]:-} ]] || union+=("$x"); done; echo "union: ${#union[@]} ${union[-1]}"
