k="# comment"; echo "[${k%%#*}]"; v="a # b"; echo "[${v%%#*}]" "[${v#*#}]" "[${v//#/X}]"; h="x#y#z"; echo ${h#*#} ${h##*#} ${h%#*}
