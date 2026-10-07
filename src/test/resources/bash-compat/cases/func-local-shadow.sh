x=g; f() { local x=l; g; echo f:$x; }; g() { echo g:$x; x=changed; }; f; echo top:$x
