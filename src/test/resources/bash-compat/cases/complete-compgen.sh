# needs: mkdir touch sort
mkdir -p d1 d2/sub; touch f1 f2 .hid d1/x
compgen -W "alpha beta gamma" -- b; echo "st=$?"
compgen -W "alpha beta" -P pre- -S -suf a
compgen -f f | sort
compgen -d d | sort
compgen -f -X '*1' f
compgen -f -X '!*1' f
compgen -W 'one two three' -X '!t*'
compgen -A function -- nosuch; echo "st=$?"
myvar_one=1; myvar_two=2; compgen -v myvar_
f_a() { :; }; f_b() { :; }; compgen -A function f_
compgen -A shopt nullg
compgen -A setopt pipef
compgen -A signal SIGTER
compgen -k fun
_c() { COMPREPLY=(x-"$2" y-"$3" "$1"); }; compgen -F _c zz
complete -W "start stop" svc
complete -F _c -o nospace mycmd
complete -d -o dirnames cdx
complete -p svc mycmd cdx
complete -p nosuch; echo "st=$?"
complete -r svc; complete -p svc; echo "st=$?"
compgen -z; echo "st=$?"
compgen -o bogus; echo "st=$?"
compgen -f nomatch; echo "st=$?"
compgen -o default -W "" f | sort
