# needs: /tmp
cd /tmp; echo | { cd /; pwd; }; pwd
cd /tmp; echo x | while read l; do cd /; done; echo "$PWD"
