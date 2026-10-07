# needs: id
[[ $PPID =~ ^[0-9]+$ ]] && [ "$PPID" != "$$" ] && echo ppid
[ "$UID" = "$(id -u)" ] && echo uid
[ "$EUID" = "$(id -u)" ] && echo euid
[ -n "$HOSTNAME" ] && echo host
