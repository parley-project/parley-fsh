# needs: mkdir touch
shopt -s globstar; mkdir -p d/e; touch d/e/x.txt d/y.txt; echo **/*.txt
