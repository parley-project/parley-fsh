# (whether OLDPWD is set at the start depends on the environment: an inherited directory stays)
start=$PWD
cd /
[ "$OLDPWD" = "$start" ] && echo "set by cd"
cd - > /dev/null && [ "$PWD" = "$start" ] && echo "back"
