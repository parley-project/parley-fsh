n=dyn; declare "$n=val"; echo $dyn; declare -i "k=2+3"; echo $k; f(){ local "$n=loc"; echo $dyn; }; f; echo $dyn; declare "1bad=x" 2>/dev/null; echo rc=$?
