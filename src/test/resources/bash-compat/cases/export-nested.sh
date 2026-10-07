# needs: sh
export A=1; sh -c 'echo $A; export B=2'; echo "[$B]"
