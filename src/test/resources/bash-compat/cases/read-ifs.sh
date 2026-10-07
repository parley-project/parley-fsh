IFS=: read -r u p rest <<< "a:b:c:d"; echo $u $p $rest
