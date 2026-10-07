tobin() { local n=$1 b=""; ((n==0)) && b=0; while ((n>0)); do b=$((n%2))$b; ((n/=2)); done; echo $b; }
for n in 0 5 255 1024; do printf '%5d %12s %4x %6o\n' $n $(tobin $n) $n $n; done
echo $(( 2#1011 )) $(( 8#17 )) $(( 16#ff )) $(( 36#zz ))
flags=0; READ=1 WRITE=2 EXEC=4; ((flags |= READ | EXEC)); ((flags & WRITE)) || echo "no write"; ((flags & EXEC)) && echo "exec set"; echo "flags=$flags"
echo $(( 1 << 31 )) $(( ~0 & 0xFF )) $(( 0x7FFFFFFF ^ 0x0F0F0F0F ))
