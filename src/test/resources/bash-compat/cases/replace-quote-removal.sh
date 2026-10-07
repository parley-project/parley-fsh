s=$'a\tb"c\\d'; s=${s//\\/\\\\}; s=${s//\"/\\\"}; s=${s//$'\t'/\\t}; echo "$s"; x=hello; echo ${x//l/"[L]"} ${x/#h/'H'}
