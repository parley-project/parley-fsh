shopt -s extglob
[[ foo.tar.gz == *.@(gz|bz2) ]] && echo archive; [[ hello == !(h*) ]] || echo nomatch; [[ abc == @(a|x)b* ]] && echo nested
