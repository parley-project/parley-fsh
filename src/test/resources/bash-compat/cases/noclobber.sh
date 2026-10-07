echo a > nc; set -C; echo b > nc; echo $?; echo c >| nc; read x < nc; echo $x
