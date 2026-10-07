x=1; unset x; echo ":$x:"; f() { echo f; }; unset -f f; type f >/dev/null 2>&1 || echo gone
