pkgs=(
  alpha   # first
  "beta gamma"

  delta
)
echo ${#pkgs[@]} "${pkgs[1]}"; opts+=(
 --one
 --two
); echo ${opts[*]}
