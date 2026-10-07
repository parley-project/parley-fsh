set -- hello ""; echo ${#1} ${#2} ${#@} ${#*}; f(){ echo ${#1}; }; f abcd
