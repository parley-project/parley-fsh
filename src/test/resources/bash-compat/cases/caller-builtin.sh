f() { read -r line fn rest <<< "$(caller 0)"; echo "$fn"; }; g() { f; }; g
