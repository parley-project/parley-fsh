s="hello world"; [[ $s == *wor* ]] && echo yes; case $s in *xyz*) echo no;; *) echo default;; esac
