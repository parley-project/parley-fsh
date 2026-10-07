printf "%d\n" 3.7 2>/dev/null; echo $?; read x <&- 2>/dev/null; echo $?
f(){ :; }; readonly -f f; f(){ echo new; } 2>/dev/null; echo $?; f; echo $?
