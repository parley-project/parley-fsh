trap "echo err \$?" ERR; f(){ false; echo in-f; }; f; set -E; f; echo end
