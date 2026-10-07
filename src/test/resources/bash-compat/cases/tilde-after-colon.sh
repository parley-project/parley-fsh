x=~/f; [ "$x" = "$HOME/f" ] && echo ok1
y=a:~/b:~; [ "$y" = "a:$HOME/b:$HOME" ] && echo ok2
echo a:~/c x=~/d | sed "s|$HOME|H|g"
