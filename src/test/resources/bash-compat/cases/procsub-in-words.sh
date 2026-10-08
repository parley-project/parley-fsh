# needs: wc tr sed
echo x<(true) | sed 's|<.*||;s|/.*||'
eval f=<(echo test4) "; cat \$f"
f() { wc -l < $1; wc -l < $1; true | wc -l < $1; }
f <(echo one) | tr -d ' '
