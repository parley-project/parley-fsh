# needs: grep
if grep -q x <<< "xyz"; then echo found; fi; if ! grep -q q <<< "xyz"; then echo missing; fi
