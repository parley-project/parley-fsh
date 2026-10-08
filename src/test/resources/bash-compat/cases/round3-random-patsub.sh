# bash's RANDOM sequence and pattern substitution rules
RANDOM=42; echo $RANDOM $RANDOM $RANDOM
s=abcdefg
echo ${s/abc/&-}
shopt -u patsub_replacement
echo ${s/abc/&-}
shopt -s patsub_replacement
r='x\&y'; echo ${s/abc/$r}
r='\\&'; echo ${s/abc/$r}
echo "${s/abc/'q'}"
shopt -s nocasematch; echo ${s/ABC/z}
