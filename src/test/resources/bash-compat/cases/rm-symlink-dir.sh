# needs: mkdir touch ln rm ls
mkdir -p sl/a; touch sl/a/x; ln -s a sl/c
rm -rf sl; echo "st=$?"
[ -e sl ] && echo still-there || echo gone
mkdir t; ln -s t u; rm -r u; [ -d t ] && echo target-kept
