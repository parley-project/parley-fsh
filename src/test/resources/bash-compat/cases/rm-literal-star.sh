# needs: mkdir rm
mkdir d; cd d
rm -rf *; echo "st=$?"
rm -f nosuch; echo "st=$?"
