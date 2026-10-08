exec 2>&1
set -x
echo $(echo inner)
x=$( (echo sub) )
PS4='+${LINENO}> '
echo after
echo "$(echo nested)"
set +x
