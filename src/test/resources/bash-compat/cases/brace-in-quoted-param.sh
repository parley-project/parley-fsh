out=a{{k}}b; key=k; k=W; echo ${out//"{{$key}}"/${!key}} ${x:-"}"} "${y:-"}"}" ${z:-'}'} "${v:-"{a}"}"
