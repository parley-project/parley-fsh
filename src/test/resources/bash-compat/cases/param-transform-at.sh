set -- "a b" c; echo ${@@Q}; a=(x "y z"); echo "${a[@]@Q}"
