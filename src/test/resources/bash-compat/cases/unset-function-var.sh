f() { :; }; f=1; unset f; type f >/dev/null 2>&1 && echo func-left
