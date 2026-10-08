trap 'echo "D[$BASH_COMMAND]"' DEBUG
x=1
f() { echo in f; }
f
if true; then echo t; fi
[[ 1 == 1 ]]
for i in a; do echo $i; done
case a in a) echo c;; esac
(( 2 > 1 ))
y=$(echo s)
trap - DEBUG
echo off
