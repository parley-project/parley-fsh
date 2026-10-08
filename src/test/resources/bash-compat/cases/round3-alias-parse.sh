# aliases are expanded when a line is read: keywords, trailing blanks, next line
shopt -s expand_aliases
alias switch=case
switch foo in foo) echo ok 1;; esac
alias foo='echo ' bar=baz baz=quux
foo bar
alias number="echo 123"
echo $(( $(number) + 1 ))
alias defined_here=echo; defined_here not used on this line 2>/dev/null || echo not yet
defined_here now it is
