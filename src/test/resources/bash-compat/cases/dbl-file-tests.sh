# needs: touch
touch f; [[ -f f && ! -d f ]] && echo file; [[ -e nope ]] || echo none
