declare -l lo=HeLLo; declare -u up=world; echo $lo $up; lo+=ABC; up=x; echo $lo $up
f(){ local -u u=abc; echo $u; }; f; declare +l lo; lo=MiX; echo $lo
