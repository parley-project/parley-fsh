# needs: wc tr ls
echo old > out.txt; > out.txt; wc -c < out.txt | tr -d " "; : > two.txt; ls two.txt
