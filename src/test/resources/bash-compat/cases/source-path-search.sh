# needs: mkdir
mkdir -p d; echo "echo sourced \$1" > d/s.sh; PATH=$PWD/d:$PATH; . s.sh arg; source s.sh
