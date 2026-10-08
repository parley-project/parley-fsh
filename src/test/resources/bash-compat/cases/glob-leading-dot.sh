# needs: touch
shopt -s extglob
touch .a .foo a b bar
echo @(.foo)
echo @(.foo|bar)
echo .*
shopt -u globskipdots
echo .*
echo *(.a|.foo)
echo @(|b)a*
