# needs: cat wc tr grep awk sort uniq head sed
cat > access.log <<'L'
10.0.0.1 - - [01/Jan/2024:10:00:00] "GET /index.html HTTP/1.1" 200 512
10.0.0.2 - - [01/Jan/2024:10:00:05] "GET /about.html HTTP/1.1" 404 128
10.0.0.1 - - [01/Jan/2024:10:01:00] "POST /api/login HTTP/1.1" 200 64
10.0.0.3 - - [01/Jan/2024:10:02:00] "GET /index.html HTTP/1.1" 200 512
10.0.0.1 - - [01/Jan/2024:10:03:00] "GET /missing HTTP/1.1" 404 0
L
echo "requests: $(wc -l < access.log | tr -d ' ')"
echo "404s: $(grep -c '" 404 ' access.log)"
awk '{print $1}' access.log | sort | uniq -c | sort -rn | head -2 | while read -r n ip; do echo "$ip $n"; done
bytes=0; while read -r _ _ _ _ _ _ _ _ _ size; do bytes=$((bytes+size)); done < access.log; echo "bytes=$bytes"
sed -n 's/.*"\([A-Z]*\) \([^ ]*\).*/\1 \2/p' access.log | sort -u
grep -o '/[a-z]*\.html' access.log | sort | uniq -c | awk '{printf "%s:%d\n", $2, $1}'
