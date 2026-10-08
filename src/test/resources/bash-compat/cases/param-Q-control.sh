x=$'a\tb'; echo "${x@Q}"
y="it's"; echo "${y@Q}"
z=$'line1\nline2\x01\\end'; echo "${z@Q}"
arr=($'a\nb' plain); echo "${arr[@]@Q}"
