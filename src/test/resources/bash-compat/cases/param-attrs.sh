declare -ri x=3; declare -a arr=(1); declare -A as=([k]=v); declare -x ex=1; declare -l lo=a; s=1
echo ${x@a} ${arr@a} ${as@a} ${ex@a} ${lo@a} "[${s@a}]"
