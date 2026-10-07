shopt -s failglob; ( echo none*.xyz ); echo "s=$?"; echo before; echo none*.xyz; echo after
