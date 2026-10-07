# needs: grep wc tr
sleep 0.3 & jobs | grep -c Running; jobs -p | grep -c .; wait; jobs | wc -l | tr -d " "
