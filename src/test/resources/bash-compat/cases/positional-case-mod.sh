set -- hello World; echo ${1^^} ${2,,} ${1^} ${2,}; f(){ echo "${1^^}!"; case ${1,,} in start) echo matched;; esac; }; f START; echo "${@^}"
