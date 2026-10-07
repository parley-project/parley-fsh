f(){ trap "echo ret \$1" RETURN; echo in; }; f a; g(){ echo g; }; g
