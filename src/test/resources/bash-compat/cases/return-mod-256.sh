f() { return 300; }; f; echo $?; (exit 257); echo $?; g() { return -1; }; g; echo $?
