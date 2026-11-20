# needs: ln mkdir touch find sort
mkdir -p d/sub; touch d/f d/sub/g; ln -s f d/lf; ln -s sub d/ls; ln -s nothing d/dang
find d -type l | sort
find d -type f | sort
find -L d -type f | sort
find -L d -type d | sort
