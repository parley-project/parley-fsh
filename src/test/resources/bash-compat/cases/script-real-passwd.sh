# needs: cut sort uniq awk tr
data='root:x:0:0:root:/root:/bin/bash
daemon:x:1:1:daemon:/usr/sbin:/usr/sbin/nologin
ann:x:1000:1000:Ann Smith,,,:/home/ann:/bin/zsh
bob:x:1001:1001::/home/bob:/bin/bash'
while IFS=: read -r user _ uid gid gecos home shell; do
  (( uid >= 1000 )) || continue
  full=${gecos%%,*}
  printf '%-5s %5d %-12s %s\n' "$user" "$uid" "${full:-(none)}" "${shell##*/}"
done <<< "$data"
shells=$(cut -d: -f7 <<< "$data" | sort | uniq -c | sort -rn | awk '{print $2"="$1}' | tr '\n' ' '); echo "$shells"
