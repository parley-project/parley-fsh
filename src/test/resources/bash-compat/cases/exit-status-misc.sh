# needs: touch chmod
touch f; chmod -x f; ./f 2>/dev/null; echo $?
local x=1 2>/dev/null; echo $?
return 2>/dev/null; echo $?
