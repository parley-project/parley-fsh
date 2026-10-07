shopt -s extglob
f=report.final.txt; echo ${f%%.@(txt|md)}; x=aaab; echo ${x##+(a)}
