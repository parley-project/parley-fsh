# needs: touch
touch -t 202001010000 old; touch -t 202101010000 new; [ new -nt old ] && echo newer; [ old -ot new ] && echo older
