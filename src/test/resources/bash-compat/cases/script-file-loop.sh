# needs: mkdir touch
mkdir d; touch d/a.txt d/b.log d/c.txt; for f in d/*.txt; do b=${f##*/}; echo "${b%.txt}"; done
