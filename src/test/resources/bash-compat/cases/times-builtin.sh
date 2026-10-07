# needs: grep
times >/dev/null && echo ok; times | grep -c "m.*s"
