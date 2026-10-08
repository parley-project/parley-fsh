# an endless producer, read until enough
while read -r l; do echo "got $l"; [ "$l" = 3 ] && break; done < <(i=0; while true; do i=$((i+1)); echo $i; done)
echo after loop
head -2 <(yes)
n=0; while read -r l; do n=$((n+1)); done < <(seq 500); echo $n
