exec 3> fd.txt; echo three >&3; exec 3>&-; read x < fd.txt; echo $x
