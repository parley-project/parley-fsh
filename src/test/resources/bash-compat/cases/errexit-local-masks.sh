set -e; f(){ local x=$(false); echo masked $?; declare y=$(false); echo d $?; }; f
z=$(false) || echo assign-fails
