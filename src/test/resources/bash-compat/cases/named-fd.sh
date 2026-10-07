exec {fd}> f.txt; echo hi >&$fd; exec {fd}>&-; cat f.txt; [ $fd -ge 10 ] && echo high-fd
