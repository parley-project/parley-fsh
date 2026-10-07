f() { echo $#; }; f a $nope b; f a "$nope" b
